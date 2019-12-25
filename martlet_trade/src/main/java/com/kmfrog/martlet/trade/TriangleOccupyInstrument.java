package com.kmfrog.martlet.trade;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kmfrog.martlet.C;
import com.kmfrog.martlet.book.IOrderBook;
import com.kmfrog.martlet.book.Instrument;
import com.kmfrog.martlet.book.PriceLevel;
import com.kmfrog.martlet.book.Side;
import com.kmfrog.martlet.book.TrackBook;
import com.kmfrog.martlet.feed.DataChangeListener;
import com.kmfrog.martlet.feed.Source;
import com.kmfrog.martlet.feed.domain.TradeLog;
import com.kmfrog.martlet.trade.config.InstrumentsJson.Param;
import com.kmfrog.martlet.trade.exec.TacCancelExec;
import com.kmfrog.martlet.trade.exec.TacPlaceOrderExec;
import com.kmfrog.martlet.trade.utils.OrderUtil;
import com.kmfrog.martlet.util.FeedUtils;

import io.broker.api.client.BrokerApiRestClient;
import it.unimi.dsi.fastutil.longs.LongBidirectionalIterator;
import it.unimi.dsi.fastutil.longs.LongSortedSet;

/**
 * 有三角关系的占领式刷量策略。 1. 抢占盘口（比如前三档）。 2. 确认是自己占领（order book & track book)时，立即下对敲单成交。 3.
 * 这个占领策略需要考虑三角盘口汇率换算关系。（hntc/btc,btc/usdt, hntc/usdt)后面以(ca, ab, cb)简称。
 * 
 * @author dust Nov 15, 2019
 *
 */
public class TriangleOccupyInstrument extends Thread implements DataChangeListener {

    protected final Logger logger = LoggerFactory.getLogger(InstrumentSoloDunk.class);

    private AtomicBoolean isQuit = new AtomicBoolean(false);
    private BlockingQueue<IOrderBook> depthQueue = new PriorityBlockingQueue<>();
    /** 当前线程主交易对 **/
    Instrument ca;
    Instrument ab;
    /** 有时候，更多的时候应该是bc **/
    Instrument cb;
    boolean reverseAb;
    Source src;
    TrackBook caTracker;
    Provider provider;
    BrokerApiRestClient client;
    int minSleepMillis;
    int maxSleepMillis;
    long vMin;
    long vMax;
    AtomicLong lastOrder = new AtomicLong(0L);
    AtomicLong lastTrade = new AtomicLong(0L);
    float chasePct = 0.8f;

    public TriangleOccupyInstrument(Instrument ca, Instrument ab, Instrument cb, boolean reverseAb, Source src,
            TrackBook caTracker, Provider provider, BrokerApiRestClient client, Param args) {
        super("TriangleOccupyInstrument-" + ca.asString() + "-" + ca.asLong());
        this.ca = ca;
        this.ab = ab;
        this.cb = cb;
        this.src = src;
        this.caTracker = caTracker;
        this.provider = provider;
        this.client = client;
        minSleepMillis = args.getMinSleepMillis();
        maxSleepMillis = args.getMaxSleepMillis();
        vMin = args.getMinVolume();
        vMax = args.getMaxVolume();
        this.reverseAb = reverseAb;
    }
    
    public void run() {
    	while(!isQuit.get()) {
    		long sleepMillis = FeedUtils.between(minSleepMillis, maxSleepMillis);
    		try {
    			IOrderBook caBook = depthQueue.poll(sleepMillis, TimeUnit.MILLISECONDS);
                if (caBook != null) {
                    provider.setOrderBook(src, ca, caBook);
                }
                IOrderBook lastBook = provider.getOrderBook(src, ca);
                if (lastBook == null) {
                    continue;
                }
                
                IOrderBook abBook = provider.getOrderBook(src, ab);
                IOrderBook cbBook = provider.getOrderBook(src, cb);
                if (abBook == null || cbBook == null) {
                    continue;
                }

                long diff = max(lastBook.getLastUpdateTs(), abBook.getLastUpdateTs(), cbBook.getLastUpdateTs())
                        - min(lastBook.getLastUpdateTs(), abBook.getLastUpdateTs(), cbBook.getLastUpdateTs());
                if (diff > C.SYMBOL_DELAY_MILLIS) {
                    continue;
                }
                
                boolean chasingAskSuccess = false;
                boolean chasingBidSuccess = false;
                long abAsk1 = abBook.getBestAskPrice();
                long cbBid1 = cbBook.getBestBidPrice();
                long abBid1 = abBook.getBestBidPrice();
                long cbAsk1 = cbBook.getBestAskPrice();
                if (abAsk1 > 0 && cbBid1 > 0) {
                    long caAskLimit = reverseAb ?  abAsk1 * cbBid1 / ca.getPriceFactor() : cbBid1 * ca.getPriceFactor() / abAsk1;
                    System.out.println("++++"+cbBid1+"|"+abAsk1+"|"+caAskLimit+"|"+reverseAb);
                    chasingAskSuccess = this.chasingAsk(lastBook, caAskLimit);
                }
                if (abBid1 > 0 && cbAsk1 > 0) {
                	long caBidLimit = reverseAb ?  abBid1 * cbAsk1 / ca.getPriceFactor() : cbAsk1 * ca.getPriceFactor() / abBid1;
                	System.out.println("----"+abBid1+"|"+cbAsk1+"|"+caBidLimit+"|"+reverseAb);
                	chasingBidSuccess = chasingBid(lastBook, caBidLimit);
                }
                
                if(chasingAskSuccess && chasingBidSuccess) {
                	// 下对敲单
                }
                
                // 清理买一卖一以外的订单
    		}catch(Exception ex) {
    			logger.warn(ex.getMessage(), ex);
    		}
    	}
    }

//    public void _run() {
//
//        while (!isQuit.get()) {
//            long sleepMillis = FeedUtils.between(minSleepMillis, maxSleepMillis);
//            try {
//                IOrderBook caBook = depthQueue.poll(sleepMillis, TimeUnit.MILLISECONDS);
//                if (caBook != null) {
//                    provider.setOrderBook(src, ca, caBook);
//                }
//
//                IOrderBook lastBook = provider.getOrderBook(src, ca);
//                if (lastBook == null) {
//                    continue;
//                }
//                System.out.println(lastBook.getOriginText(src, provider.getMaxLevel()));
//
//                boolean placed = false;
//                PriceLevel openAskLevel = caTracker.getBestLevel(Side.SELL);
//                if (System.currentTimeMillis() - lastTrade.get() > sleepMillis && openAskLevel != null) {
//                    long bestAskPrice = lastBook.getBestAskPrice();
//                    long bestAskSize = bestAskPrice > 0 ? lastBook.getAskSize(bestAskPrice) : 0L;
//                    if (bestAskSize > 0 && bestAskPrice == openAskLevel.getPrice()
//                            && openAskLevel.getSize() / bestAskSize * 1.0 >= 0.9) {
//                        if (bestAskSize < vMin) {
//                            // 小于最小订单数量，先撤单（因为api会拒绝）
//                            Set<Long> cancelIds = openAskLevel.getOrderIds();
//                            TacCancelExec cancelExec = new TacCancelExec(cancelIds, client, provider, caTracker);
//                            provider.submitExec(cancelExec);
//                        } else {
//                            TacPlaceOrderExec placeBid = new TacPlaceOrderExec(ca, bestAskPrice, bestAskSize, Side.BUY,
//                                    client, caTracker);
//                            provider.submitExec(placeBid);
//                            lastTrade.set(System.currentTimeMillis());
//                            placed = true;
//                        }
//                    }
//                }
//
//                PriceLevel openBidLevel = caTracker.getBestLevel(Side.BUY);
//                if (System.currentTimeMillis() - lastTrade.get() > sleepMillis && openBidLevel != null) {
//                    long bestBidPrice = lastBook.getBestBidPrice();
//                    long bestBidSize = bestBidPrice > 0 ? lastBook.getBidSize(bestBidPrice) : 0;
//                    if (bestBidSize > 0 && bestBidPrice == openBidLevel.getPrice()
//                            && openBidLevel.getSize() / bestBidSize * 1.0 >= 0.9) {
//                        if (bestBidSize < vMin) {
//                            // 小于最小订单数量，先撤单（因为api会拒绝）
//                            Set<Long> cancelIds = openBidLevel.getOrderIds();
//                            TacCancelExec cancelExec = new TacCancelExec(cancelIds, client, provider, caTracker);
//                            provider.submitExec(cancelExec);
//                        } else {
//                            TacPlaceOrderExec placeBid = new TacPlaceOrderExec(ca, bestBidPrice, bestBidSize, Side.SELL,
//                                    client, caTracker);
//                            provider.submitExec(placeBid);
//                            lastTrade.set(System.currentTimeMillis());
//                            placed = true;
//                        }
//                    }
//                }
//
//                if (placed) {
//                    // 处理下一次order book变化及推送。
//                    continue;
//                }
//
//                IOrderBook abBook = provider.getOrderBook(src, ab);
//                IOrderBook cbBook = provider.getOrderBook(src, cb);
//                if (abBook == null || cbBook == null) {
//                    continue;
//                }
//
//                long diff = max(lastBook.getLastUpdateTs(), abBook.getLastUpdateTs(), cbBook.getLastUpdateTs())
//                        - min(lastBook.getLastUpdateTs(), abBook.getLastUpdateTs(), cbBook.getLastUpdateTs());
//                if (diff > C.SYMBOL_DELAY_MILLIS) {
//                    continue;
//                }
//
//                long caBid1 = lastBook.getBestBidPrice();
//                long caAsk1 = lastBook.getBestAskPrice();
//                long probability = System.currentTimeMillis() % 10;
//
//                if (/* probability < 5 && */ System.currentTimeMillis() - lastOrder.get() > sleepMillis && !hasOccupy(Side.SELL, lastBook)) {
//                    long abAsk1 = abBook.getBestAskPrice();
//                    long cbBid1 = cbBook.getBestBidPrice();
//
//                    if (abAsk1 > 0 && cbBid1 > 0) {
//                        long caAskLimit = cbBid1 / abAsk1 * ca.getPriceFactor();
//
//                        if ((caAsk1 - caAskLimit) / (caAsk1 * 1.0) > 0.001) {
//                            // 距离3角套利还有安全距离
//                            long price = caAsk1 - ca.getPriceFactor() / ca.getShowPriceFactor();
//                            long volume = FeedUtils.between(vMin, vMax);
//                            TacPlaceOrderExec place = new TacPlaceOrderExec(ca, price, volume, Side.SELL, client,
//                                    caTracker);
//                            provider.submitExec(place);
//                            lastOrder.set(System.currentTimeMillis());
//
//                        }
//                    }
//
//                }
////                else if (System.currentTimeMillis() - lastOrder.get() > sleepMillis && !hasOccupy(Side.BUY, lastBook)) {
////                    long abBid1 = abBook.getBestBidPrice();
////                    long cbAsk1 = cbBook.getBestAskPrice();
////
////                    if (abBid1 > 0 && cbAsk1 > 0) {
////                        long caBidLimit = cbAsk1 / abBid1 * ca.getPriceFactor();
////
////                        if ((caBidLimit - caBid1) / (caBid1 * 1.0) > 0.001) {
////                            // 距离3角套利还有安全距离
////                            long price = caBid1 + ca.getPriceFactor() / ca.getShowPriceFactor();
////                            long volume = FeedUtils.between(vMin, vMax);
////                            TacPlaceOrderExec place = new TacPlaceOrderExec(ca, price, volume, Side.BUY, client,
////                                    caTracker);
////                            provider.submitExec(place);
////                            lastOrder.set(System.currentTimeMillis());
////                        }
////                    }
////                }
//
//                // 撤掉3档以外所有订单。
//                cancelAfterLevel3Ask(lastBook);
//                cancelAfterLevel3Bid(lastBook);
//
//            } catch (InterruptedException ex) {
//                logger.warn(ex.getMessage(), ex);
//            } catch (Exception ex) {
//                logger.warn(ex.getMessage(), ex);
//            }
//        }
//    }

    private void cancelAfterLevel3Ask(IOrderBook caBook) {
        LongSortedSet prices = caBook.getAskPrices();
        long level3 = prices.size() > 3 ? prices.toArray(new long[prices.size()])[2] : 0;
        if (level3 > 0) {
            Set<Long> afterLevel3 = caTracker.getOrdersBetter(Side.SELL, level3);
            if (afterLevel3.size() > 0) {
                TacCancelExec cancelExec = new TacCancelExec(afterLevel3, client, provider, caTracker);
                provider.submitExec(cancelExec);
            }
        }
    }

    private void cancelAfterLevel3Bid(IOrderBook caBook) {
        LongSortedSet prices = caBook.getBidPrices();
        long level3 = prices.size() > 3 ? prices.toArray(new long[prices.size()])[2] : 0;

        if (level3 > 0) {
            Set<Long> afterLevel3 = caTracker.getOrdersBetter(Side.BUY, level3);
            if (afterLevel3.size() > 0) {
                TacCancelExec cancelExec = new TacCancelExec(afterLevel3, client, provider, caTracker);
                provider.submitExec(cancelExec);
            }
        }
    }

    /**
     * 某价位是否已经被占领？
     * 
     * @param side
     * @param price
     * @param obSize
     * @return
     */
    boolean hasOccupy(Side side, IOrderBook book) {
        PriceLevel openLevel = caTracker.getBestLevel(side);
        if (openLevel == null || book.getBestAskPrice() == 0 || book.getBestAskPrice() == 0) {
            return false;
        }

        long bboPrice = side == Side.SELL ? book.getBestAskPrice() : book.getBestBidPrice();
        if (openLevel.getPrice() != bboPrice) {
            return false;
        }
        long bboSize = side == Side.SELL ? book.getAskSize(bboPrice) : book.getBidSize(bboPrice);
        return openLevel.getSize() / bboSize * 1.0 >= 0.9;
    }

    static long min(long... numbers) {
        if (numbers.length == 1) {
            return numbers[0];
        }
        long min = Math.min(numbers[0], numbers[1]);
        for (int i = 2; i < numbers.length; i++) {
            min = Math.min(min, numbers[i]);
        }
        return min;
    }

    static long max(long... numbers) {
        if (numbers.length == 1) {
            return numbers[0];
        }
        long max = Math.max(numbers[0], numbers[1]);
        for (int i = 2; i < numbers.length; i++) {
            max = Math.min(max, numbers[i]);
        }
        return max;
    }

    @Override
    public void onDepth(Long instrument, IOrderBook book) {
        try {
            depthQueue.put(book);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onTrade(Long instrument, TradeLog tradeLog) {
        // TODO Auto-generated method stub

    }
    
	private boolean chasingAsk(IOrderBook lastBook, long caAskLimit) {
		long bestBidPrice = lastBook.getBestBidPrice();
		long bestAskPrice = lastBook.getBestAskPrice();
		PriceLevel openAskLevel = caTracker.getBestLevel(Side.SELL);
		
		//因为涉及到精度问题,这里计算最小单位的价格
		long unitPrice = 1 * C.POWERS_OF_TEN[ca.getPriceFractionDigits() - ca.getShowPriceFractionDigits()];
		long bestAskSize = lastBook.getAskSize(bestAskPrice);
		long secondAskSize = lastBook.getAskSize(bestAskPrice + unitPrice); // 卖一价格增加一个单位价格的订单数量,如果为0说明卖一 卖二间隔大于一个价位
		long chasePrice = bestAskPrice - unitPrice; // 价格比卖一少一个价位
		long followChaseSize = (long) Math.ceil(bestAskSize/(1-chasePct)); // 跟随下单数量
		followChaseSize = followChaseSize < vMin ? vMin : followChaseSize;
		long chaseMax = vMax * 100; // 最大跟随数量
		followChaseSize = followChaseSize > chaseMax ? chaseMax : followChaseSize;
		long chaseSize = FeedUtils.between(vMin, vMax); // 绝对占领下单数量
		
		if((chasePrice - caAskLimit)/(chasePrice * 1.0) <= 0.001) {
		// 如果占领存在三角套利风险,取消所有open订单,等待
			System.out.println("# 存在三角套利风险,取消所有open订单,等待"+ca.asString()+"|"+chasePrice+"|"+caAskLimit);
			Set<Long> cancelIds = caTracker.getOrders(Side.SELL);
			TacCancelExec cancelExec = new TacCancelExec(cancelIds, client, provider, caTracker);
			provider.submitExec(cancelExec);
			return false;
		}
		
		if(openAskLevel == null) { 
		// 没有开放的订单,需要检查盘口并占领卖一
			if(chasePrice <= bestBidPrice) { 
			// 盘口没有空间了,只能按照百分比跟随卖一
				System.out.println(String.format("# 盘口没有空间+跟随卖一 %d|%d", bestAskPrice, followChaseSize));
//				TacPlaceOrderExec placeAsk = new TacPlaceOrderExec(instrument, bestAskPrice, followChaseSize, Side.SELL, client, trackBook);
//				provider.submitExec(placeAsk);
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
					// 绝对占领卖一,且卖一卖二间隔一个价位,占领成功
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
				// 买一没有绝对占领,但是满足占比,认为占领成功
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
	
	private boolean chasingBid(IOrderBook lastBook, long caBidLimit) {
		long bestBidPrice = lastBook.getBestBidPrice();
		long bestAskPrice = lastBook.getBestAskPrice();
		PriceLevel openBidLevel = caTracker.getBestLevel(Side.BUY);

		long unitPrice = 1 * C.POWERS_OF_TEN[ca.getPriceFractionDigits() - ca.getShowPriceFractionDigits()];
		long bestBidSize = lastBook.getBidSize(bestBidPrice);
		long secondBidSize = lastBook.getBidSize(bestBidPrice - unitPrice); // 买一价格减少一个单位价格的订单数量,如果为0说明买一 买二间隔大于一个价位
		long chasePrice = bestBidPrice + unitPrice; // 价格比买一多一个价位
		long followChaseSize = (long) Math.ceil(bestBidSize/(1-chasePct)); // 跟随下单数量
		followChaseSize = followChaseSize < vMin ? vMin : followChaseSize;
		long chaseMax = vMax * 100; // 最大跟随数量
		followChaseSize = followChaseSize > chaseMax ? chaseMax : followChaseSize;
		long chaseSize = FeedUtils.between(vMin, vMax); // 绝对占领下单数量
		
		if((caBidLimit - chasePrice)/(caBidLimit * 1.0) <= 0.001) {
			// 如果占领存在三角套利风险,取消所有open订单,等待
				System.out.println("# 存在三角套利风险,取消所有open订单,等待"+ca.asString()+"|"+chasePrice+"|"+caBidLimit);
				Set<Long> cancelIds = caTracker.getOrders(Side.SELL);
				TacCancelExec cancelExec = new TacCancelExec(cancelIds, client, provider, caTracker);
				provider.submitExec(cancelExec);
				return false;
		}
		
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
	

}
