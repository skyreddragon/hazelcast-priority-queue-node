package com.potapov.hazelcast.node;

import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * Created by днс on 15.01.2017.
 */
public class Node {
    public static void main(String[] args) {
        ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext("classpath:spring-hazelcast.xml");
    }
}
