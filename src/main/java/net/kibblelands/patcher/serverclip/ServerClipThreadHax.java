package net.kibblelands.patcher.serverclip;

public class ServerClipThreadHax extends Thread implements Thread.UncaughtExceptionHandler {
    private static final UncaughtExceptionHandler noOp = (thread, throwable) -> {};
    private final UnstableRunnable unstableRunnable;
    private Throwable throwable;
    private boolean exec;

    public ServerClipThreadHax(UnstableRunnable unstableRunnable) {
        this.unstableRunnable = unstableRunnable;
        this.setUncaughtExceptionHandler(this);
        this.setDaemon(true);
    }

    @Override
    public void setContextClassLoader(ClassLoader cl) {
        if (exec && Thread.currentThread() == this) {
            this.setUncaughtExceptionHandler(noOp);
            this.interrupt();
            throw new Error();
        } else {
            super.setContextClassLoader(cl);
        }
    }

    @Override
    public void run() {
        if (!exec && Thread.currentThread() == this) {
            exec = true;
            try {
                this.unstableRunnable.run();
            } catch (Throwable t) {
                this.getUncaughtExceptionHandler()
                        .uncaughtException(this, t);
            }
        }
    }

    public void exec() throws Exception {
        if (this.exec) throw new IllegalStateException();
        this.start();
        this.join();
        if (this.throwable != null) {
            propagate(this.throwable);
        }
    }

    @Override
    public void uncaughtException(Thread thread, Throwable throwable) {
        this.throwable = throwable;
    }

    private static void propagate(Throwable throwable) throws Exception {
        if (throwable instanceof Error) {
            throw (Error) throwable;
        } else {
            throw (Exception)
                    (throwable instanceof Exception ?
                            throwable : new Exception(throwable));
        }
    }

    interface UnstableRunnable {
        void run() throws Throwable;
    }
}