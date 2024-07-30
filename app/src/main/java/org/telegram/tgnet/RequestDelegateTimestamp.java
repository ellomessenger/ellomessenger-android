package org.telegram.tgnet;

import org.telegram.tgnet.tlrpc.TLObject;

public interface RequestDelegateTimestamp {
    void run(TLObject response, TLRPC.TL_error error, long responseTime);
}
