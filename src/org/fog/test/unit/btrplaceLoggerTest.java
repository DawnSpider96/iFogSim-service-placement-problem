package org.fog.test.unit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class btrplaceLoggerTest {
    private static final Logger log = LoggerFactory.getLogger(btrplaceLoggerTest.class);

    public static void main(String[] args) {
        log.info("🎉 SLF4J + Logback are working!");
    }
}