package com.kmfrog.martlet.trade.tac;

import java.util.Set;

import com.kmfrog.martlet.C;
import com.kmfrog.martlet.book.IOrderBook;
import com.kmfrog.martlet.book.Instrument;
import com.kmfrog.martlet.book.PriceLevel;
import com.kmfrog.martlet.book.Side;
import com.kmfrog.martlet.book.TrackBook;
import com.kmfrog.martlet.feed.Source;
import com.kmfrog.martlet.trade.InstrumentChaser;
import com.kmfrog.martlet.trade.Provider;
import com.kmfrog.martlet.trade.config.InstrumentsJson.Param;
import com.kmfrog.martlet.trade.exec.TacCancelExec;
import com.kmfrog.martlet.trade.exec.TacPlaceOrderExec;
import com.kmfrog.martlet.trade.utils.OrderUtil;
import com.kmfrog.martlet.util.FeedUtils;

import io.broker.api.client.BrokerApiRestClient;
import it.unimi.dsi.fastutil.longs.LongSortedSet;

public class TacInstrumentChaser extends InstrumentChaser{
	
	private final BrokerApiRestClient client;
	private final float chasePct = 0.7f;

	public TacInstrumentChaser(Instrument instrument, Source src, TrackBook trackBook, Provider provider, Param args, BrokerApiRestClient client) {
		super(instrument, src, trackBook, provider, args);
		this.client = client;
	}

	@Override
	public void chasing(IOrderBook lastBook) {
		System.out.println("=================");
		chasingAsk(lastBook);
		chasingBid(lastBook);
		
		
		//取消2档以外的订单(考虑到对敲单,这里简单处理成取消2档意外,严格来说应该取消一档以外的)
//		cancelAfterLevel2Ask(lastBook);
//		cancelAfterLevel2Bid(lastBook);
	}
	
	private boolean chasingAsk(IOrderBook lastBook) {		
		long bestBidPrice = lastBook.getBestBidPrice();
		long bestAskPrice = lastBook.getBestAskPrice();
		PriceLevel openAskLevel = trackBook.getBestLevel(Side.SELL);
		
		long unitPrice = 1 * C.POWERS_OF_TEN[instrument.getPriceFractionDigits() - instrument.getShowPriceFractionDigits()];
		
		long bestAskSize = lastBook.getAskSize(bestAskPrice);
		long secondAskSize = lastBook.getAskSize(bestAskPrice + unitPrice); // 卖一价格增加一个单位价格的订单数量,如果为0说明卖一 卖二间隔大于一个价位
		long chasePrice = bestAskPrice - unitPrice; // 价格比卖一少一个价位
		long followChaseSize = (long) Math.ceil(bestAskSize/(1-chasePct)); // 跟随下单数量
		followChaseSize = followChaseSize < vMin ? vMin : followChaseSize;
		long chaseMax = vMax * 10; // 最大跟随数量
		followChaseSize = followChaseSize > chaseMax ? chaseMax : followChaseSize;
		long chaseSize = FeedUtils.between(vMin, vMax); // 绝对占领下单数量
		
		if(openAskLevel == null) { 
		// 没有开放的订单,需要检查盘口并占领卖一
			if(chasePrice <= bestBidPrice) { 
			// 盘口没有空间了,只能按照百分比跟随卖一
				System.out.println(lastBook.getOriginText(Source.Bhex, 5));
				System.out.println(String.format("# 盘口没有空间+跟随卖一 %d|%d", bestAskPrice, followChaseSize));
//				TacPlaceOrderExec placeAsk = new TacPlaceOrderExec(instrument, bestAskPrice, followChaseSize, Side.SELL, client, trackBook);
//	            provider.submitExec(placeAsk);
				return false;
			} else { 
			// 盘口还有空间,绝对占领卖一
				System.out.println(String.format("# 盘口还有空间+占领卖一 %d|%d", chasePrice, chaseSize));
//				TacPlaceOrderExec placeAsk = new TacPlaceOrderExec(instrument, chasePrice, chaseSize, Side.SELL, client, trackBook);
//	            provider.submitExec(placeAsk);
				return false;
			}
		}else{
		// 存在开放的订单,检查占领情况
			if(openAskLevel.getPrice() == bestAskPrice) { 
			// 已经占领了卖一
				Set<String> clientOrderIds = openAskLevel.getClientOrderIds();
				for(String clientOrderId: clientOrderIds) {
					if(OrderUtil.isHedgeOrder(clientOrderId)) { 
					// 如果卖一为对敲单,不处理等待对敲结束
						return false;
					}
				}
				
				long openAskSize = openAskLevel.getSize();
				if(openAskSize == bestAskSize) {
				// 卖一绝对占领,检查与卖二是否间隔一个价位
					if(secondAskSize == 0) {
					// 卖一与卖二间隔大于一个价位,取消绝对占领的卖一
						Set<Long> cancelIds = openAskLevel.getOrderIds();
						System.out.println("## 卖一卖二间隔大于一个价位,取消占领");
//                        TacCancelExec cancelExec = new TacCancelExec(cancelIds, client, provider, trackBook);
//                        provider.submitExec(cancelExec);
                        return false;	
					}else {
					// 绝对占领卖一,且卖一卖二间隔一个价位
						System.out.println("## 绝对占领卖一,且卖一卖二间隔一个价位");
						return true;
					}
				}else if(openAskSize * 1.0 / bestAskSize < chasePct) {
				// 卖一没有绝对占领,并且未达到理想占比
					if(chasePrice <= bestBidPrice) { 
					// 盘口没有空间了
						if(openAskSize >= chaseMax) {
						// 如果下单数量已经达到跟随最大数量,也认为占领成功!
							System.out.println("## 如果下单数量已经达到跟随最大数量,也认为占领成功!");
							return true;
						}else {
						// 调整跟随占比(取消订单,交给下一个event重新下单)
							System.out.println("## 调整跟随占比(取消订单,交给下一个event重新下单)");
//							Set<Long> cancelIds = openAskLevel.getOrderIds();
//	                        TacCancelExec cancelExec = new TacCancelExec(cancelIds, client, provider, trackBook);
//	                        provider.submitExec(cancelExec);
	                        return false;	
						}
					}else { 
					// 盘口还有空间,对卖一进行绝对占领
						System.out.println(String.format("## 盘口还有空间+占领卖一 %d|%d", chasePrice, chaseSize));
//						TacPlaceOrderExec placeAsk = new TacPlaceOrderExec(instrument, chasePrice, chaseSize, Side.SELL, client, trackBook);
//			            provider.submitExec(placeAsk);
						return false;
					}
				}else {
				// 买一没有绝对占领,满足占比,认为占领成功
					System.out.println("## 买一没有绝对占领,满足占比,认为占领成功");
					return true;
				}
			}else { 
			// 未占领卖一
				if(chasePrice <= bestBidPrice) { 
				// 盘口没有空间了,跟随卖一
					System.out.println(String.format("### 盘口没有空间了,跟随卖一 %d|%d", bestBidPrice, followChaseSize));
//					TacPlaceOrderExec placeAsk = new TacPlaceOrderExec(instrument, bestBidPrice, followChaseSize, Side.SELL, client, trackBook);
//		            provider.submitExec(placeAsk);
					return false;
				}else {
				// 盘口还有空间,绝对占领买一
					System.out.println(String.format("### 盘口还有空间,绝对占领卖一 %d|%d", chasePrice, chaseSize));
//					TacPlaceOrderExec placeAsk = new TacPlaceOrderExec(instrument, chasePrice, chaseSize, Side.SELL, client, trackBook);
//		            provider.submitExec(placeAsk);
					return false;
				}
			}
		}
	}
	
	private boolean chasingBid(IOrderBook lastBook) {
		long bestBidPrice = lastBook.getBestBidPrice();
		long bestAskPrice = lastBook.getBestAskPrice();
		PriceLevel openBidLevel = trackBook.getBestLevel(Side.BUY);

		long unitPrice = 1 * C.POWERS_OF_TEN[instrument.getPriceFractionDigits() - instrument.getShowPriceFractionDigits()];
		
		long bestBidSize = lastBook.getBidSize(bestBidPrice);
		long secondBidSize = lastBook.getBidSize(bestBidPrice - unitPrice); // 买一价格减少一个单位价格的订单数量,如果为0说明买一 买二间隔大于一个价位
		long chasePrice = bestBidPrice + unitPrice; // 价格比买一多一个价位
		long followChaseSize = (long) Math.ceil(bestBidSize/(1-chasePct)); // 跟随下单数量
		followChaseSize = followChaseSize < vMin ? vMin : followChaseSize;
		long chaseMax = vMax * 10; // 最大跟随数量
		followChaseSize = followChaseSize > chaseMax ? chaseMax : followChaseSize;
		long chaseSize = FeedUtils.between(vMin, vMax); // 绝对占领下单数量
		
		if(openBidLevel == null) { 
		// 没有开放的订单,需要检查盘口并占领买一
			if(chasePrice >= bestAskPrice) { 
			// 盘口没有空间了,只能按照百分比跟随买一
				System.out.println(String.format("# 盘口没有空间了,只能按照百分比跟随买一 %d|%d", bestBidPrice, followChaseSize));
//				TacPlaceOrderExec placeBid = new TacPlaceOrderExec(instrument, bestBidPrice, followChaseSize, Side.BUY, client, trackBook);
//	            provider.submitExec(placeBid);
				return false;
			} else { 
			// 盘口还有空间,绝对占领买一
				System.out.println(String.format("# 盘口还有空间,绝对占领买一 %d|%d", chasePrice, chaseSize));
//				TacPlaceOrderExec placeBid = new TacPlaceOrderExec(instrument, chasePrice, chaseSize, Side.BUY, client, trackBook);
//	            provider.submitExec(placeBid);
				return false;
			}
		}else{
		// 存在开放的订单,检查占领情况
			if(openBidLevel.getPrice() == bestBidPrice) { 
			// 已经占领了买一
				Set<String> clientOrderIds = openBidLevel.getClientOrderIds();
				for(String clientOrderId: clientOrderIds) {
					if(OrderUtil.isHedgeOrder(clientOrderId)) { 
					// 如果买一为对敲单,不处理等待对敲结束
						return false;
					}
				}
				
				long openBidSize = openBidLevel.getSize();
				if(openBidSize == bestBidSize) {
				// 买一绝对占领,检查与买二是否间隔一个价位
					if(secondBidSize == 0) {
					// 买一与买二间隔大于一个价位,取消绝对占领的买一
						System.out.println("## 买一与买二间隔大于一个价位,取消绝对占领的买一"+bestBidPrice+"|"+chasePrice);
//						Set<Long> cancelIds = openBidLevel.getOrderIds();
//                        TacCancelExec cancelExec = new TacCancelExec(cancelIds, client, provider, trackBook);
//                        provider.submitExec(cancelExec);
                        return false;	
					}else {
					// 绝对占领买一,且买一买二间隔一个价位
						System.out.println("## 绝对占领买一,且买一买二间隔一个价位");
						return true;
					}
				}else if(openBidSize * 1.0 / bestBidSize < chasePct) {
				// 买一没有绝对占领,并且未达到理想占比
					if(chasePrice >= bestAskPrice) { 
					// 盘口没有空间了
						if(openBidSize>=chaseMax) {
						// 如果下单数量已经达到跟随最大数量,也认为占领成功!
							System.out.println("## 如果下单数量已经达到跟随最大数量,也认为占领成功!");
							return true;
						}else {
						// 调整跟随占比(取消订单,交给下一个event重新下单)
							System.out.println("## 取消订单,交给下一个event重新下单");
//							Set<Long> cancelIds = openBidLevel.getOrderIds();
//	                        TacCancelExec cancelExec = new TacCancelExec(cancelIds, client, provider, trackBook);
//	                        provider.submitExec(cancelExec);
	                        return false;	
						}
					}else { 
					// 盘口还有空间,对买一进行绝对占领
						System.out.println(String.format("## 盘口还有空间,对买一进行绝对占领 %d|%d", chasePrice, chaseSize));
//						TacPlaceOrderExec placeBid = new TacPlaceOrderExec(instrument, chasePrice, chaseSize, Side.BUY, client, trackBook);
//			            provider.submitExec(placeBid);
						return false;
					}
				}else {
				// 买一没有绝对占领,满足占比,认为占领成功
					System.out.println("## 买一没有绝对占领,满足占比,认为占领成功"+openBidSize+"|"+bestBidSize);
					return true;
				}
			}else { 
			// 未占领买一
				if(chasePrice >= bestAskPrice) { 
				// 盘口没有空间了,跟随买一
					System.out.println(String.format("### 盘口没有空间了,跟随买一 %d|%d", bestBidPrice, followChaseSize));
//					TacPlaceOrderExec placeBid = new TacPlaceOrderExec(instrument, bestBidPrice, followChaseSize, Side.BUY, client, trackBook);
//		            provider.submitExec(placeBid);
					return false;
				}else {
				// 盘口还有空间,绝对占领买一
					System.out.println(String.format("### 盘口还有空间,对买一进行绝对占领 %d|%d", chasePrice, chaseSize));
//					TacPlaceOrderExec placeBid = new TacPlaceOrderExec(instrument, chasePrice, chaseSize, Side.BUY, client, trackBook);
//		            provider.submitExec(placeBid);
					return false;
				}
			}
		}
	}

	private void cancelAfterLevel2Ask(IOrderBook lastBook) {
        LongSortedSet prices = lastBook.getAskPrices();
        long level2 = prices.size() > 2 ? prices.toArray(new long[prices.size()])[1] : 0;
        if (level2 > 0) {
            Set<Long> afterLevel2 = trackBook.getOrdersBetter(Side.SELL, level2);
            if (afterLevel2.size() > 0) {
                TacCancelExec cancelExec = new TacCancelExec(afterLevel2, client, provider, trackBook);
                provider.submitExec(cancelExec);
            }
        }
    }

    private void cancelAfterLevel2Bid(IOrderBook lastBook) {
        LongSortedSet prices = lastBook.getBidPrices();
        long level2 = prices.size() > 2 ? prices.toArray(new long[prices.size()])[1] : 0;

        if (level2 > 0) {
            Set<Long> afterLevel2 = trackBook.getOrdersBetter(Side.BUY, level2);
            if (afterLevel2.size() > 0) {
                TacCancelExec cancelExec = new TacCancelExec(afterLevel2, client, provider, trackBook);
                provider.submitExec(cancelExec);
            }
        }
    }
}
