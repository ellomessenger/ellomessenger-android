/*
 * This is the source code of tgnet library v. 1.1
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2015-2018.
 * Copyright Nikita Denin, Ello 2024.
 */

#ifndef EVENTOBJECT_H
#define EVENTOBJECT_H

#include <cstdint>
#include "Defines.h"

class EventObject {

public:
    EventObject(void *object, EventObjectType type);

    void onEvent(uint32_t events) const;

    int64_t time = 0;
    void *eventObject;
    EventObjectType eventType;
};

#endif
