/*
 * Copyright 2021 OPS4J.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package javax.resource.spi;

import javax.transaction.xa.XAException;
import javax.transaction.xa.Xid;

/**
 * @version $Rev$ $Date$
 */
public interface XATerminator {

    void commit(Xid xid, boolean onePhase) throws XAException;

    void forget(Xid xid) throws XAException;

    int prepare(Xid xid) throws XAException;

    Xid[] recover(int flag) throws XAException;

    void rollback(Xid xid) throws XAException;

}