/*
 * This is the source code of tgnet library v. 1.1
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2015-2018.
 * Copyright Nikita Denin, Ello 2024.
 */

#ifndef CONNECTION_H
#define CONNECTION_H

#include <pthread.h>
#include <vector>
#include <string>
#include <openssl/aes.h>
#include "ConnectionSession.h"
#include "ConnectionSocket.h"
#include "ExponentialBackoffStrategy.h"
#include "Defines.h"

class Datacenter;

class Timer;

class ByteStream;

class ByteArray;

class Connection : public ConnectionSession, public ConnectionSocket {

public:

    Connection(Datacenter *datacenter, ConnectionType type, int8_t num);

    ~Connection() override;

    void connect();

    void suspendConnection();

    void suspendConnection(bool idle);

    void sendData(NativeByteBuffer *buffer, bool reportAck, bool encrypted);

    bool hasUsefulData();

    void setHasUsefulData();

    bool allowsCustomPadding();

    [[nodiscard]] uint32_t getConnectionToken() const;

    ConnectionType getConnectionType();

    [[nodiscard]] int8_t getConnectionNum() const;

    Datacenter *getDatacenter();

    bool isSuspended();

    static bool isMediaConnectionType(ConnectionType type);

    [[nodiscard]] bool canReconnect() const;

protected:

    void onReceivedData(NativeByteBuffer *buffer) override;

    void onDisconnected(int32_t reason, int32_t error) override;

    void onConnected() override;

    bool hasPendingRequests() override;

    void reconnect();

    void resetAllRetries();

private:

    enum TcpConnectionState {
        TcpConnectionStageIdle,
        TcpConnectionStageConnecting,
        TcpConnectionStageReconnecting,
        TcpConnectionStageConnected,
        TcpConnectionStageSuspended
    };

    enum ProtocolType {
        ProtocolTypeEF,
        ProtocolTypeEE,
        ProtocolTypeDD,
        ProtocolTypeTLS
    };

    inline void encryptKeyWithSecret(uint8_t *array, uint8_t secretType);

    inline std::string *getCurrentSecret(uint8_t secretType);

    void onDisconnectedInternal(int32_t reason, int32_t error);

    void retryWithBackoff();

    void discardConnectionAfterTimeout();

    ProtocolType currentProtocolType = ProtocolTypeEE;

    ExponentialBackoffStrategy backoffStrategy = ExponentialBackoffStrategy(1000L, 2.4, 16000L);
    TcpConnectionState connectionState = TcpConnectionStageIdle;
    uint32_t connectionToken = 0;
    std::string hostAddress;
    std::string secret;
    uint16_t hostPort = 0;
    uint16_t failedConnectionCount = 0;
    Datacenter *currentDatacenter = nullptr;
    uint32_t currentAddressFlags = 0;
    ConnectionType connectionType = ConnectionTypeGeneric;
    int8_t connectionNum;
    bool firstPacketSent = false;
    NativeByteBuffer *restOfTheData = nullptr;
    uint32_t lastPacketLength = 0;
    bool hasSomeDataSinceLastConnect = false;
    bool isTryingNextPort = false;
    bool wasConnected = false;
    uint32_t willRetryConnectCount = 5;
    Timer *reconnectTimer;
    bool usefulData = false;
    bool forceNextPort = false;
    bool isMediaConnection = false;
    bool waitForReconnectTimer = false;
    bool connectionInProcess = false;
    int64_t usefulDataReceiveTime = 0;
    uint32_t currentTimeout = 4;
    uint32_t receivedDataAmount = 0;

    uint8_t temp[64] = {0};

    AES_KEY encryptKey = {};
    uint8_t encryptIv[16] = {0};
    uint32_t encryptNum = 0;
    uint8_t encryptCount[16] = {0};

    AES_KEY decryptKey = {};
    uint8_t decryptIv[16] = {0};
    uint32_t decryptNum = 0;
    uint8_t decryptCount[16] = {0};

    uint32_t baseReconnectTimeout = 1000;
    int maxReconnectRetries = 5;
    int retryReconnectAttempt = 0;
    uint32_t lastReconnectTimeout = baseReconnectTimeout;

    friend class ConnectionsManager;
};

#endif
