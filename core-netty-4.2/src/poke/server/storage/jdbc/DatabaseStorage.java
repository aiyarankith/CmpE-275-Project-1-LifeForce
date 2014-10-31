/*
 * copyright 2014, gash
 * 
 * Gash licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package poke.server.storage.jdbc;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import poke.server.storage.TenantStorage;

import com.google.common.primitives.Bytes;
import com.google.protobuf.ByteString;
import com.jolbox.bonecp.BoneCP;
import com.jolbox.bonecp.BoneCPConfig;

import eye.Comm.JobDesc;
import eye.Comm.NameSpace;

public class DatabaseStorage implements TenantStorage {
	protected static Logger logger = LoggerFactory.getLogger("database");

	/*public static final String sDriver = "com.mysql.jdbc.Driver";
	public static final String sUrl = "jdbc:mysql://localhost/test";
	public static final String sUser = "root";
	public static final String sPass = "root";*/

	protected Properties cfg;
	protected static BoneCP cpool;
	protected String schema;
	
	protected DatabaseStorage() {
	}

	public DatabaseStorage(Properties cfg) {
		init(cfg);
	}

	@Override
	public void init(Properties cfg) {
		if (cpool != null)
			return;

		this.cfg = cfg;

		try {			
			Class.forName(cfg.getProperty("DB_DRIVER_CLASS"));
			BoneCPConfig config = new BoneCPConfig();
			config.setJdbcUrl(cfg.getProperty("DB_URL"));
			config.setUsername(cfg.getProperty("DB_USERNAME"));
			config.setPassword(cfg.getProperty("DB_PASSWORD"));
			config.setMinConnectionsPerPartition(5);
			config.setMaxConnectionsPerPartition(10);
			config.setPartitionCount(1);

			cpool = new BoneCP(config);
			System.out.println("POlllllllllllllllllllllllllllllll");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see gash.jdbc.repo.Repository#release()
	 */
	@Override
	public void release() {
		if (cpool == null)
			return;

		cpool.shutdown();
		cpool = null;
	}

	@Override
	public NameSpace getNameSpaceInfo(long spaceId) {
		NameSpace space = null;

		Connection conn = null;
		try {
			conn = cpool.getConnection();
			conn.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
			// TODO complete code to retrieve through JDBC/SQL
			// select * from space where id = spaceId

			if (conn != null){
				System.out.println("Connection successful!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
				Statement stmt =  conn.createStatement();
				ResultSet rs = stmt.executeQuery("SELECT * FROM test"); // do something with the connection.
				while(rs.next()){
					System.out.println(rs.getString(1)); // should print out "1"'
				}
			}
			logger.info("This is inside JDBC ................................................");
		} catch (Exception ex) {
			ex.printStackTrace();
			logger.error("failed/exception on looking up space " + spaceId, ex);
			try {
				conn.rollback();
			} catch (SQLException e) {
			}
		} finally {
			if (conn != null) {
				try {
					conn.close();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}

		return space;
	}

	@Override
	public List<NameSpace> findNameSpaces(NameSpace criteria) {
		List<NameSpace> list = null;

		Connection conn = null;
		try {
			conn = cpool.getConnection();
			conn.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
			// TODO complete code to search through JDBC/SQL
		} catch (Exception ex) {
			ex.printStackTrace();
			logger.error("failed/exception on find", ex);
			try {
				conn.rollback();
			} catch (SQLException e) {
			}
		} finally {
			if (conn != null) {
				try {
					conn.close();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}

		return list;
	}

	@Override
	public NameSpace createNameSpace(NameSpace space) {
		if (space == null)
			return space;

		Connection conn = null;
		try {
			conn = cpool.getConnection();
			conn.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
			// TODO complete code to use JDBC
		} catch (Exception ex) {
			ex.printStackTrace();
			logger.error("failed/exception on creating space " + space, ex);
			try {
				conn.rollback();
			} catch (SQLException e) {
			}

			// indicate failure
			return null;
		} finally {
			if (conn != null) {
				try {
					conn.close();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}

		return space;
	}

	@Override
	public boolean removeNameSpace(long spaceId) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean addJob(String namespace, JobDesc job) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean removeJob(String namespace, String jobId) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean updateJob(String namespace, JobDesc job) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public List<JobDesc> findJobs(String namespace, JobDesc criteria) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String addImage(String photoname, ByteString data) {
		System.out.println("INSIDE CONNEEEEEEEEEEEEEEEEEEEEEEEEt");
		Connection conn = null;
		String insertId = null;
		try {
			conn = cpool.getConnection();
			System.out.println("INSIDE CONNEEEEEEEEEEEEEEEEEEEEEEEEt CPOOOOOOOOOOOOOOOOOL::::::::::::  "+conn);
			conn.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
			// TODO complete code to retrieve through JDBC/SQL
			// select * from space where id = spaceId
			
			String insert = "insert into ImageStore (name, image) values('" + photoname + "','" + data + "');"; 
			String select = "SELECT LAST_INSERT_ID();";
			if (conn != null){
				System.out.println("Connection successful!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
				Statement stmt =  conn.createStatement();
				stmt.executeUpdate(insert);
				ResultSet rs = stmt.executeQuery(select);
				 while (rs.next()) {
					 System.out.println("IDDDDDDDDDDDDDDDDDDDDDd::::::::::::: "+rs.getLong("last_insert_id()"));
					 insertId = rs.getString("last_insert_id()"); 
				 }
				 
				
				logger.debug("Storing Image " + photoname + " - ");
				return insertId;
			}
			logger.info("This is inside JDBC ................................................");

		}catch (Exception ex) {
			ex.printStackTrace();
			//logger.error("failed/exception on looking up space " + spaceId, ex);
		} finally {
			if (conn != null) {
				try {
					conn.close();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
		return insertId;
	}
}
