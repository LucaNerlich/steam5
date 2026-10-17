package org.steam5.job.blurhash;

final class Base83 {

    static final char[] ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz#$%*+,-.:;=?@[]^_{|}~"
            .toCharArray();

    private Base83() {
    }

    static void encode(long value, int length, char[] buffer, int offset) {
        int exp = 1;
        for (int i = 1; i <= length; i++, exp *= 83) {
            int digit = (int) (value / exp % 83);
            buffer[offset + length - i] = ALPHABET[digit];
        }
    }
}
