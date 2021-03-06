/*
 * Licensed to CRATE Technology GmbH ("Crate") under one or more contributor
 * license agreements.  See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.  Crate licenses
 * this file to you under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.  You may
 * obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * However, if you have executed another commercial license agreement
 * with Crate these terms will supersede the license and you may use the
 * software solely pursuant to the terms of the relevant commercial agreement.
 */

package io.crate.client.jdbc;

import io.crate.client.CrateClient;
import io.crate.shade.org.elasticsearch.client.transport.NoNodeAvailableException;

import java.sql.*;
import java.util.*;
import java.util.concurrent.Executor;

public class CrateConnection implements Connection {

    private final ClientHandleRegistry.ClientHandle clientHandle;
    private boolean readOnly;
    private String schema = null;
    private CrateDatabaseMetaData metaData;
    private String databaseVersion;
    private Properties properties = new Properties();

    public CrateConnection(ClientHandleRegistry.ClientHandle handle, Properties properties) {
        this.clientHandle = handle;
        this.readOnly = false;
        this.properties = properties;
    }

    public CrateConnection(ClientHandleRegistry.ClientHandle handle) {
        this(handle, new Properties());
    }

    public CrateClient client() {
        return clientHandle.client();
    }

    public void connect() throws SQLException {
        try {
            metaData = new CrateDatabaseMetaData(this);
            databaseVersion = metaData.getDatabaseProductVersion();
        } catch (NoNodeAvailableException e) {
            close();
            throw new SQLException(String.format(Locale.ENGLISH, "Connect to '%s' failed", getUrl()), e);
        }
    }

    @Override
    public Statement createStatement() throws SQLException {
        checkClosed();
        return new CrateStatement(this);
    }

    @Override
    public PreparedStatement prepareStatement(String sql) throws SQLException {
        checkClosed();
        return new CratePreparedStatement(this, sql);
    }

    @Override
    public CallableStatement prepareCall(String sql) throws SQLException {
        checkClosed();
        throw new SQLFeatureNotSupportedException("Connection: prepareCall not supported");
    }

    @Override
    public String nativeSQL(String sql) throws SQLException {
        checkClosed();
        return sql;
    }

    @Override
    public void setAutoCommit(boolean autoCommit) throws SQLException {
        checkClosed();
        if (!autoCommit && strict()) {
            throw new SQLFeatureNotSupportedException("The auto-commit mode cannot be disabled. " +
                    "The Crate JDBC driver does not support manual commit.");
        }
    }

    private boolean strict() {
        return Boolean.valueOf(properties.getProperty("strict", "false"));
    }

    @Override
    public boolean getAutoCommit() throws SQLException {
        checkClosed();
        return true;
    }

    @Override
    public void commit() throws SQLException {
        checkClosed();
        if (getAutoCommit() && strict()) {
            throw new SQLFeatureNotSupportedException("The commit operation is not allowed. " +
                    "The Crate JDBC driver does not support manual commit.");
        }
    }

    @Override
    public void rollback() throws SQLException {
        checkClosed();
        throwIfStrictMode("Rollback is not supported.");
    }

    @Override
    public void close() throws SQLException {
        metaData = null;
        clientHandle.connectionClosed();
    }

    @Override
    public boolean isClosed() {
        return metaData == null;
    }

    @Override
    public DatabaseMetaData getMetaData() throws SQLException {
        checkClosed();
        return metaData;
    }

    @Override
    public void setReadOnly(boolean readOnly) throws SQLException {
        checkClosed();
        this.readOnly = readOnly;
    }

    /**
     * Crate does not distinguish between read-only and write mode.
     * A CrateConnection is always in write mode, even the readOnly flag is set.
     *
     * @throws SQLException
     */
    @Override
    public boolean isReadOnly() throws SQLException {
        return this.readOnly;
    }

    @Override
    public void setCatalog(String catalog) throws SQLException {
        checkClosed();
        throw new SQLFeatureNotSupportedException("Catalog is not supported");
    }

    @Override
    public String getCatalog() throws SQLException {
        checkClosed();
        return null;
    }

    @Override
    public void setTransactionIsolation(int level) throws SQLException {
        checkClosed();
    }

    @Override
    public int getTransactionIsolation() throws SQLException {
        checkClosed();
        return Connection.TRANSACTION_NONE;
    }

    @Override
    public SQLWarning getWarnings() throws SQLException {
        checkClosed();
        return null;
    }

    @Override
    public void clearWarnings() throws SQLException {
        checkClosed();
    }

    @Override
    public Statement createStatement(int resultSetType, int resultSetConcurrency) throws SQLException {
        if (!metaData.supportsResultSetConcurrency(resultSetType, resultSetConcurrency)) {
            throw new SQLFeatureNotSupportedException(String.format("Connection: createStatement(int resultSetType, int resultSetConcurrency) is not supported " +
                    "with arguments: resultSetType=%d, resultSetConcurrency=%d", resultSetType, resultSetConcurrency));
        }
        return createStatement();
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
        if (!metaData.supportsResultSetConcurrency(resultSetType, resultSetConcurrency)) {
            throw new SQLFeatureNotSupportedException(String.format("Connection: prepareStatement(String sql, int resultSetType, int resultSetConcurrency) is not supported " +
                            "with arguments: sql=\"%s\", resultSetType=%d, resultSetConcurrency=%d",
                    sql, resultSetType, resultSetConcurrency));
        }
        return prepareStatement(sql);
    }

    @Override
    public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
        checkClosed();
        throw new SQLFeatureNotSupportedException("Connection: prepareCall(String sql, int resultSetType, int resultSetConcurrency) not supported");
    }

    @Override
    public Map<String, Class<?>> getTypeMap() throws SQLException {
        checkClosed();
        throw new SQLFeatureNotSupportedException("Connection: getTypeMap not supported");
    }

    @Override
    public void setTypeMap(Map<String, Class<?>> map) throws SQLException {
        checkClosed();
    }

    @Override
    public void setHoldability(int holdability) throws SQLException {
        checkClosed();
    }

    @Override
    public int getHoldability() throws SQLException {
        return ResultSet.HOLD_CURSORS_OVER_COMMIT;
    }

    @Override
    public Savepoint setSavepoint() throws SQLException {
        checkClosed();
        throwIfStrictMode("Savepoint is not supported.");
        return null;
    }

    @Override
    public Savepoint setSavepoint(String name) throws SQLException {
        checkClosed();
        throwIfStrictMode("Savepoint is not supported.");
        return null;
    }

    @Override
    public void rollback(Savepoint savepoint) throws SQLException {
        checkClosed();
        throwIfStrictMode("Rollback is not supported.");
    }

    @Override
    public void releaseSavepoint(Savepoint savepoint) throws SQLException {
        checkClosed();
        throwIfStrictMode("Savepoint is not supported.");
    }

    @Override
    public Statement createStatement(int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
        if (!metaData.supportsResultSetConcurrency(resultSetType, resultSetConcurrency) ||
                !metaData.supportsResultSetHoldability(resultSetHoldability)) {
            throw new SQLFeatureNotSupportedException(String.format("Connection: createStatement(int resultSetType, int resultSetConcurrency, int resultSetHoldability) is not supported " +
                    "with arguments: resultSetType=%d, resultSetConcurrency=%d, resultSetHoldability=%d", resultSetType, resultSetConcurrency, resultSetHoldability));
        }
        return createStatement();
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
        if (!metaData.supportsResultSetConcurrency(resultSetType, resultSetConcurrency) ||
                !metaData.supportsResultSetHoldability(resultSetHoldability)) {
            throw new SQLFeatureNotSupportedException(String.format("Connection: prepareStatement(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) is not supported " +
                            "with arguments: sql=\"%s\", resultSetType=%d, resultSetConcurrency=%d, resultSetHoldability=%d",
                    sql, resultSetType, resultSetConcurrency, resultSetHoldability));
        }
        return prepareStatement(sql);
    }

    @Override
    public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
        checkClosed();
        throw new SQLFeatureNotSupportedException("Connection: prepareCall(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) not supported");
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) throws SQLException {
        checkClosed();
        throw new SQLFeatureNotSupportedException("Connection: prepareStatement(String sql, int autoGeneratedKeys) not supported");
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int[] columnIndexes) throws SQLException {
        checkClosed();
        throw new SQLFeatureNotSupportedException("Connection: prepareStatement(String sql, int[] columnIndexes) not supported");
    }

    @Override
    public PreparedStatement prepareStatement(String sql, String[] columnNames) throws SQLException {
        checkClosed();
        throw new SQLFeatureNotSupportedException("Connection: prepareStatement(String sql, String[] columnNames) not supported");
    }

    @Override
    public Clob createClob() throws SQLException {
        checkClosed();
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public Blob createBlob() throws SQLException {
        checkClosed();
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public NClob createNClob() throws SQLException {
        checkClosed();
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public SQLXML createSQLXML() throws SQLException {
        checkClosed();
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public boolean isValid(int timeout) throws SQLException {
        return !isClosed();
    }

    @Override
    public void setClientInfo(String name, String value) throws SQLClientInfoException {
        if (isClosed()) {
            throw new SQLClientInfoException();
        }
        if (value != null) {
            this.properties.setProperty(name, value);
        } else {
            this.properties.remove(name);
        }
    }

    @Override
    public void setClientInfo(Properties properties) throws SQLClientInfoException {
        if (isClosed()) {
            throw new SQLClientInfoException();
        }
        if (properties == null || properties.isEmpty()) {
            this.properties.clear();
        } else {
            this.properties = properties;
        }
    }

    @Override
    public String getClientInfo(String name) throws SQLException {
        checkClosed();
        return properties.getProperty(name);
    }

    @Override
    public Properties getClientInfo() throws SQLException {
        checkClosed();
        return properties;
    }

    @Override
    public Array createArrayOf(String typeName, Object[] elements) throws SQLException {
        checkClosed();
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public Struct createStruct(String typeName, Object[] attributes) throws SQLException {
        checkClosed();
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void setSchema(String schema) throws SQLException {
        checkClosed();
        if (VersionStringComparator.compareVersions(databaseVersion, CrateDatabaseMetaData.CRATE_REQUEST_DEFAULT_SCHEMA) < 0) {
            // according to JDBC java docs the driver should silently ignore it if it is not supported.
            return;
        }
        this.schema = schema;
    }

    @Override
    public String getSchema() throws SQLException {
        checkClosed();
        return schema;
    }

    @Override
    public void abort(Executor executor) throws SQLException {
        checkClosed();
        throw new SQLFeatureNotSupportedException("Connection: abort not supported");
    }

    @Override
    public void setNetworkTimeout(Executor executor, int milliseconds) throws SQLException {
    }

    @Override
    public int getNetworkTimeout() throws SQLException {
        return 0;
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (iface.isAssignableFrom(getClass())) {
            return (T) this;
        }
        throw new SQLException("Cannot unwrap to " + iface.getName());
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return iface.isAssignableFrom(getClass());
    }

    private void checkClosed() throws SQLException {
        if (isClosed()) {
            throw new SQLException("Connection is closed");
        }
    }

    private void throwIfStrictMode(String message) throws SQLException {
        boolean strict = Boolean.valueOf(properties.getProperty("strict"));
        if (strict) {
            throw new SQLFeatureNotSupportedException(message);
        }
    }

    public String getUrl() {
        return clientHandle.url();
    }
}
