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

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.sql.*;
import java.util.Collection;
import java.util.InvalidPropertiesFormatException;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.logging.Logger;

public class CrateDriver implements Driver {

    private static final String PROTOCOL = "jdbc";
    private static final String SUB_PROTOCOL = "crate";
    public static final String PREFIX = SUB_PROTOCOL + ":" + "//";
    public static final String LONG_PREFIX = PROTOCOL + ":" + SUB_PROTOCOL + ":" + "//";
    private final ClientHandleRegistry clientHandleRegistry = new ClientHandleRegistry();

    static {
        try {
            DriverManager.registerDriver(new CrateDriver());
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public Collection<String> clientURLs() {
        return clientHandleRegistry.urls();
    }

    public CrateDriver() {
    }


    @Override
    public Connection connect(String url, Properties info) throws SQLException {
        if (url.startsWith(LONG_PREFIX)) {
            url = url.substring(LONG_PREFIX.length());
        } else if (url.startsWith(PREFIX)) {
            url = url.substring(PREFIX.length());
        } else {
            return null;
        }

        try {
            url = parseUrl(url, info);
        } catch (InvalidPropertiesFormatException | UnsupportedEncodingException e) {
            throw new SQLException(e);
        }

        ClientHandleRegistry.ClientHandle handle = clientHandleRegistry.getHandle(url);
        CrateConnection connection = new CrateConnection(handle, info);
        connection.connect();

        if (!url.equals("/")) {
            String[] urlParts = url.split("/");
            if (urlParts.length == 2) {
                connection.setSchema(urlParts[1]);
            } else if (urlParts.length > 2) {
                connection.close();
                throw new SQLException("URL format is invalid. " +
                        "Valid format is: [jdbc:]crate://[host1:port1][, host2:port2 ...][/schema][?property=value]");
            }
            connection.setClientInfo(info);
        }
        return connection;
    }

    protected String parseUrl(String url, Properties info) throws UnsupportedEncodingException, InvalidPropertiesFormatException {
        if (info == null) {
            info = new Properties();
        }
        String[] urlParts = url.split("\\?", 2);

        if (urlParts.length == 2) {
            StringTokenizer queryParams = new StringTokenizer(urlParts[1], "&");

            while (queryParams.hasMoreTokens()) {
                String parameterValuePair = queryParams.nextToken();

                int indexOfEquals = parameterValuePair.indexOf("=");

                String parameter = null;
                String value = null;

                if (indexOfEquals != -1) {
                    parameter = parameterValuePair.substring(0, indexOfEquals);

                    if (indexOfEquals + 1 < parameterValuePair.length()) {
                        value = parameterValuePair.substring(indexOfEquals + 1);
                    }
                }

                if ((value != null && value.length() > 0)
                        && (parameter != null && parameter.length() > 0)
                        && (!value.contains("?") && !value.contains("="))) {
                    info.setProperty(parameter, URLDecoder.decode(value, "UTF-8"));
                } else {
                    throw new InvalidPropertiesFormatException("Properties format is invalid. " +
                            "Valid format is: property=value&property=value,...");
                }
            }
        }
        return urlParts[0];
    }

    @Override
    public boolean acceptsURL(String url) throws SQLException {
        return url.startsWith(PREFIX) || url.startsWith(LONG_PREFIX);
    }

    @Override
    public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
        return new DriverPropertyInfo[0];
    }

    @Override
    public int getMajorVersion() {
        return CrateDriverVersion.CURRENT.major;
    }

    @Override
    public int getMinorVersion() {
        return CrateDriverVersion.CURRENT.minor;
    }

    @Override
    public boolean jdbcCompliant() {
        return false;
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("No parent logger");
    }
}
