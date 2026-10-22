package io.iftech.healthreminder;

final class MembershipPolicy {
    static final int FREE_ITEM_LIMIT = 3;
    static final int FREE_ARTWORK_COUNT = 3;

    private MembershipPolicy() { }

    static boolean requiresMembershipForNewItem(int currentCount) {
        return currentCount >= FREE_ITEM_LIMIT;
    }

    static boolean requiresMembershipForArtwork(int index) {
        return index >= FREE_ARTWORK_COUNT;
    }

    static boolean requiresMembershipForFocusMode(boolean focusMode) {
        return focusMode;
    }
}
