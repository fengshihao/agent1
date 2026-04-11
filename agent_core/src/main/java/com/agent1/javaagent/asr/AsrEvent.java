package com.agent1.javaagent.asr;

public abstract class AsrEvent {
    private AsrEvent() {}

    public static final class Started extends AsrEvent {}

    public static final class Partial extends AsrEvent {
        private final String text;

        public Partial(String text) {
            this.text = text;
        }

        public String getText() {
            return text;
        }
    }

    public static final class Final extends AsrEvent {
        private final String text;

        public Final(String text) {
            this.text = text;
        }

        public String getText() {
            return text;
        }
    }

    public static final class Error extends AsrEvent {
        private final String message;

        public Error(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }
    }

    public static final class Completed extends AsrEvent {}
}
