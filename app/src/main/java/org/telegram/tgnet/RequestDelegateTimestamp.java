package org.telegram.tgnet;

import org.telegram.tgnet.tlrpc.TLObject;
import org.telegram.tgnet.tlrpc.TL_error;

public interface RequestDelegateTimestamp {
    void run(TLObject response, TL_error error, long responseTime);
}
