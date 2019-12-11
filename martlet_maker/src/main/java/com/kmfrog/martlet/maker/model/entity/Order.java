/*

 * Copyright (c) 2018 superatomfin.com. All Rights Reserved.

 */
package com.kmfrog.martlet.maker.model.entity;

import java.math.BigDecimal;
import java.util.Date;

import com.kmfrog.martlet.book.Side;


public class Order {
    private Long id;
    private Integer userId;
    private Side side;
	private BigDecimal price;
    private BigDecimal volume;
    private BigDecimal remainVolume;
    private String feeRateMaker;
    private String feeRateTaker;
    private BigDecimal fee;
    private String feeCoinRate;
    private BigDecimal dealVolume;
    private BigDecimal dealMoney;
    private BigDecimal avgPrice;
    private Integer status;
//    private OrderType type;
//    private OrderSource source;
    private Date ctime;
    public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Integer getUserId() {
		return userId;
	}

	public void setUserId(Integer userId) {
		this.userId = userId;
	}

	public Side getSide() {
		return side;
	}

	public void setSide(Side side) {
		this.side = side;
	}

	public BigDecimal getPrice() {
		return price;
	}

	public void setPrice(BigDecimal price) {
		this.price = price;
	}

	public BigDecimal getVolume() {
		return volume;
	}

	public void setVolume(BigDecimal volume) {
		this.volume = volume;
	}

	public BigDecimal getRemainVolume() {
		return remainVolume;
	}

	public void setRemainVolume(BigDecimal remainVolume) {
		this.remainVolume = remainVolume;
	}

	public String getFeeRateMaker() {
		return feeRateMaker;
	}

	public void setFeeRateMaker(String feeRateMaker) {
		this.feeRateMaker = feeRateMaker;
	}

	public String getFeeRateTaker() {
		return feeRateTaker;
	}

	public void setFeeRateTaker(String feeRateTaker) {
		this.feeRateTaker = feeRateTaker;
	}

	public BigDecimal getFee() {
		return fee;
	}

	public void setFee(BigDecimal fee) {
		this.fee = fee;
	}

	public String getFeeCoinRate() {
		return feeCoinRate;
	}

	public void setFeeCoinRate(String feeCoinRate) {
		this.feeCoinRate = feeCoinRate;
	}

	public BigDecimal getDealVolume() {
		return dealVolume;
	}

	public void setDealVolume(BigDecimal dealVolume) {
		this.dealVolume = dealVolume;
	}

	public BigDecimal getDealMoney() {
		return dealMoney;
	}

	public void setDealMoney(BigDecimal dealMoney) {
		this.dealMoney = dealMoney;
	}

	public BigDecimal getAvgPrice() {
		return avgPrice;
	}

	public void setAvgPrice(BigDecimal avgPrice) {
		this.avgPrice = avgPrice;
	}

	public Date getCtime() {
		return ctime;
	}

	public void setCtime(Date ctime) {
		this.ctime = ctime;
	}

	public Date getMtime() {
		return mtime;
	}

	public void setMtime(Date mtime) {
		this.mtime = mtime;
	}

	public String getTableName() {
		return tableName;
	}

	public void setTableName(String tableName) {
		this.tableName = tableName;
	}

	public String getSymbol() {
		return symbol;
	}

	public void setSymbol(String symbol) {
		this.symbol = symbol;
	}

	public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }



    private Date mtime;
    private String tableName;
    private String symbol;

    public static Order buildOrderByPriceLevel(String symbol, Side side, BigDecimal price, BigDecimal volume, Integer userId) {
//        if (priceLevelWrapper == null) {
//            return null;
//        }
        Order order = new Order();
        order.setSide(side);
        order.setPrice(price);
        order.setVolume(volume);
        order.setSymbol(symbol);
        order.setUserId(userId);
//        order.setSide(priceLevelWrapper.getOrderSide());
//        order.setPrice(priceLevelWrapper.getPrice());
//        order.setVolume(priceLevelWrapper.getVolume());
        return order;
    }
    
//    public static class Builder{
//        private Long id;
//        private Integer userId;
//        private Side side;
//        private BigDecimal price;
//        private BigDecimal volume;
//        private BigDecimal remainVolume;
//        private String feeRateMaker;
//        private String feeRateTaker;
//        private BigDecimal fee;
//        private String feeCoinRate;
//        private BigDecimal dealVolume;
//        private BigDecimal dealMoney;
//        private BigDecimal avgPrice;
////        private OrderStatus status;
////        private OrderType type;
////        private OrderSource source;
//        private Date ctime;
//        private Date mtime;
//        private String tableName;
//        private String symbol;
//        
//        
//    }
}