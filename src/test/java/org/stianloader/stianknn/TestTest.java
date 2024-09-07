package org.stianloader.stianknn;

import java.io.IOException;
import java.io.UncheckedIOException;

public class TestTest {

    public static void main(String[] args) {
        TestStarGenerator tsg;
        try {
            tsg = new TestStarGenerator();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        tsg.generateStars(100000);
    }

}
