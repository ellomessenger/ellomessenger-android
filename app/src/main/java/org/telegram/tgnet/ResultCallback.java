package org.telegram.tgnet;

import org.telegram.tgnet.tlrpc.TL_error;

public interface ResultCallback<T> {

    void onComplete(T result);

    default void onError(TL_error error) {}

    default void onError(Throwable throwable) {}
}
