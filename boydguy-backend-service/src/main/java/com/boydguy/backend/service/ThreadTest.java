package com.boydguy.backend.service;

public class ThreadTest implements Runnable {

    private BoydguyService boydguyService;
    private int timeout;

    public ThreadTest(BoydguyService boydguyService, int timeout) {
        this.boydguyService = boydguyService;
        this.timeout = timeout;
    }

    @Override
    public void run() {
        try {
            boydguyService.testRedisLock(timeout);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

}
