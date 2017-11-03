package org.learn.reactive.java8;

public interface PurchaseAction<T> {
	public void execute(T t) throws Exception;
}
