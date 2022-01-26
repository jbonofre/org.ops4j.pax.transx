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
package org.ops4j.pax.transx.jdbc;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Objects;
import javax.sql.DataSource;
import javax.sql.XADataSource;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.ops4j.pax.transx.tm.Transaction;
import org.ops4j.pax.transx.tm.TransactionManager;
import org.ops4j.pax.transx.tm.impl.geronimo.GeronimoPlatformTransactionManager;
import org.ops4j.pax.transx.tm.impl.geronimo.TransactionManagerWrapper;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

public class H2Test {

    private static final String DROP_USER = "DROP TABLE IF EXISTS USERS CASCADE";
    private static final String CREATE_TABLE_USER = "CREATE TABLE IF NOT EXISTS USERS (ID INT NOT NULL UNIQUE, NAME VARCHAR(50))";
    private static final String INSERT_INTO_USER = "INSERT INTO USERS (ID, NAME) VALUES (?, ?)";
    private static final String SELECT_FROM_USER_BY_ID = "SELECT * FROM USERS WHERE ID = ?";
    private static final String DELETE_FROM_USER_BY_ID = "DELETE FROM USERS WHERE ID = ?";
    private static final String DELETE_FROM_USER = "DELETE * FROM USERS";
    private static final String COUNT_USER = "SELECT COUNT(*) FROM USERS";

    PlatformTransactionManager ptm;
    TransactionManager tm;

    public static class User {
        private int id;
        private String name;

        public User() {
        }

        public User(int id, String name) {
            this.id = id;
            this.name = name;
        }

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            User user = (User) o;
            return id == user.id &&
                    Objects.equals(name, user.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, name);
        }
    }

    @Before
    public void setUp() throws Exception {
        GeronimoPlatformTransactionManager atm = new GeronimoPlatformTransactionManager();
        tm = new TransactionManagerWrapper(atm);
        ptm = atm;
    }

    @Test
    public void testContextWithXaTx() throws Exception {
        DataSource ds = wrap(createH2DataSource());

        Transaction tx = tm.begin();
        try (Connection con = ds.getConnection()) {
            try (Statement st = con.createStatement()) {
                st.execute(DROP_USER);
                st.execute(CREATE_TABLE_USER);
            }
        }
        tx.commit();

        tx = tm.begin();
        try (Connection con = ds.getConnection()) {
            try (PreparedStatement ps = con.prepareStatement(INSERT_INTO_USER)) {
                ps.setInt(1, 1);
                ps.setString(2, "user1");
                ps.executeUpdate();
            }
        }
        tx.commit();

        tx = tm.begin();
        try (Connection con = ds.getConnection()) {
            PreparedStatement ps = con.prepareStatement(SELECT_FROM_USER_BY_ID);
            ps.setInt(1, 1);
            ResultSet rs = ps.executeQuery();
            Statement st2 = rs.getStatement();
            assertSame(ps, st2);
        }
        tx.commit();

        try (Connection con = ds.getConnection()) {
            DatabaseMetaData dmd = con.getMetaData();
            ResultSet rs = dmd.getSchemas();
            assertNull(rs.getStatement());
            Connection con2 = dmd.getConnection();
            assertSame(con, con2);
        }
    }

    @Test
    public void testConnectionsWithTx() throws Exception {
        DataSource ds = wrap(createH2DataSource());

        Transaction tx = tm.begin();
        try {
            try (Connection con = ds.getConnection()) {
                try (Connection con2 = ds.getConnection()) {
                    assertNotSame(con, con2);
                }
            }
            tx.commit();
        } catch (Throwable t) {
            tx.rollback();
            throw t;
        }
    }

    @Test
    public void testSpring() throws Exception {
        DataSource ds = wrap(createH2DataSource());
        JdbcTemplate jdbc = new JdbcTemplate(ds);

        jdbc.execute(DROP_USER);
        jdbc.execute(CREATE_TABLE_USER);

        jdbc.update(INSERT_INTO_USER, 1, "user1");
        User user = jdbc.queryForObject(SELECT_FROM_USER_BY_ID, new BeanPropertyRowMapper<>(User.class), 1);
        assertEquals(new User(1, "user1"), user);

        jdbc.update(DELETE_FROM_USER_BY_ID, 1);
    }

    @Test
    public void testSpringXaTx() throws Exception {
        DataSource ds = wrap(createH2DataSource());
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        TransactionTemplate tx = new TransactionTemplate(ptm);

        jdbc.execute(DROP_USER);
        jdbc.execute(CREATE_TABLE_USER);

        tx.execute(ts -> jdbc.update(INSERT_INTO_USER, 1, "user1"));
        User user = tx.execute(ts -> jdbc.queryForObject(SELECT_FROM_USER_BY_ID, new BeanPropertyRowMapper<>(User.class), 1));
        assertEquals(new User(1, "user1"), user);
        tx.execute(ts -> jdbc.update(DELETE_FROM_USER_BY_ID, 1));

        tx.execute(ts -> {
            int nb = jdbc.update(INSERT_INTO_USER, 1, "user1");
            ts.setRollbackOnly();
            return nb;
        });
        try {
            user = tx.execute(ts -> jdbc.queryForObject(SELECT_FROM_USER_BY_ID, new BeanPropertyRowMapper<>(User.class), 1));
            fail("Expected a EmptyResultDataAccessException");
        } catch (EmptyResultDataAccessException e) {
            // expected
        }
    }

    @Test
    public void testSpringLocalTx() throws Exception {
        DataSource ds = wrap(createH2DataSource());
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        TransactionTemplate tx = new TransactionTemplate(new DataSourceTransactionManager(ds));

        jdbc.execute(DROP_USER);
        jdbc.execute(CREATE_TABLE_USER);

        tx.execute(ts -> jdbc.update(INSERT_INTO_USER, 1, "user1"));
        User user = tx.execute(ts -> jdbc.queryForObject(SELECT_FROM_USER_BY_ID, new BeanPropertyRowMapper<>(User.class), 1));
        assertEquals(new User(1, "user1"), user);
        tx.execute(ts -> jdbc.update(DELETE_FROM_USER_BY_ID, 1));

        tx.execute(ts -> {
            int nb = jdbc.update(INSERT_INTO_USER, 1, "user1");
            ts.setRollbackOnly();
            return nb;
        });
        try {
            user = tx.execute(ts -> jdbc.queryForObject(SELECT_FROM_USER_BY_ID, new BeanPropertyRowMapper<>(User.class), 1));
            fail("Expected a EmptyResultDataAccessException");
        } catch (EmptyResultDataAccessException e) {
            // expected
        }
    }

    @Test
    @Ignore
    public void testBenchPreparedStatement() throws Exception {
        System.err.println("No cache");
        doBenchPs(0);
        System.err.println("PS cache");
        doBenchPs(10);
    }

    protected void doBenchPs(int psCacheSize) throws Exception {
        DataSource ds = wrap(createH2DataSource(), psCacheSize);

        Transaction tx = tm.begin();
        try (Connection con = ds.getConnection()) {
            try (Statement st = con.createStatement()) {
                st.execute(DROP_USER);
                st.execute(CREATE_TABLE_USER);
            }
        }
        tx.commit();

        tx = tm.begin();
        try (Connection con = ds.getConnection()) {
            try (PreparedStatement ps = con.prepareStatement(INSERT_INTO_USER)) {
                ps.setInt(1, 1);
                ps.setString(2, "user1");
                ps.executeUpdate();
            }
        }
        tx.commit();

        for (int l = 0; l < 10; l++) {
            System.gc();
            long t0 = System.nanoTime();
            for (int i = 0; i < 100000; i++) {
                tx = tm.begin();
                try (Connection con = ds.getConnection()) {
                    PreparedStatement ps = con.prepareStatement(SELECT_FROM_USER_BY_ID);
                    ps.setInt(1, 1);
                    ResultSet rs = ps.executeQuery();
                    Statement st2 = rs.getStatement();
                    assertSame(ps, st2);
                }
                tx.commit();
            }
            long t1 = System.nanoTime();
            if (l > 0) {
                System.err.println("Duration: " + (((t1 - t0) + 500_000) / 1_000_000) + "ms");
            }
        }
    }


    private DataSource wrap(XADataSource xaDs) throws Exception {
        return wrap(xaDs, 0);
    }

    private DataSource wrap(XADataSource xaDs, int psCacheSize) throws Exception {
        return ManagedDataSourceBuilder.builder()
                .transactionManager(tm)
                .name("h2invm")
                .dataSource(xaDs)
                .preparedStatementCacheSize(psCacheSize)
                .build();
    }

    private XADataSource createH2DataSource() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:test");
        ds.setUser("sa");
        ds.setPassword("");
        return ds;
    }
}
