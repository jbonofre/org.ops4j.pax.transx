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
package org.ops4j.pax.transx.jdbc.stubs;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * @author Brett Wooldridge
 */
public class StubDriver implements Driver {

    private static final Driver DRIVER;
    private static long connectionDelay;

    static {
        DRIVER = new StubDriver();
        try {
            DriverManager.registerDriver(DRIVER);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void setConnectDelayMs(final long delay) {
        connectionDelay = delay; //MILLISECONDS.toNanos(delay);
    }

    /** {@inheritDoc} */
    @Override
    public Connection connect(String url, Properties info) throws SQLException {
        if (connectionDelay > 0) {
//         final long start = nanoTime();
//         do {
//            // spin
//         } while (nanoTime() - start < connectionDelayNs);
            try {
                Thread.sleep(connectionDelay);
            } catch (InterruptedException var3) {
                Thread.currentThread().interrupt();
            }
        }

        return new StubConnection();
    }

    /** {@inheritDoc} */
    @Override
    public boolean acceptsURL(String url) throws SQLException {
        return true;
    }

    /** {@inheritDoc} */
    @Override
    public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public int getMajorVersion() {
        return 0;
    }

    /** {@inheritDoc} */
    @Override
    public int getMinorVersion() {
        return 0;
    }

    /** {@inheritDoc} */
    @Override
    public boolean jdbcCompliant() {
        return true;
    }

    /** {@inheritDoc} */
    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return null;
    }

}
