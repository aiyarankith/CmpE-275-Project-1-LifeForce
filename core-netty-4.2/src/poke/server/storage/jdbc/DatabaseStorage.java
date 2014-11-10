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

	/*
	 * public static final String sDriver = "com.mysql.jdbc.Driver"; public
	 * static final String sUrl = "jdbc:mysql://localhost/test"; public static
	 * final String sUser = "root"; public static final String sPass = "root";
	 */

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

			if (conn != null) {
				System.out
						.println("Connection successful!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
				Statement stmt = conn.createStatement();
				ResultSet rs = stmt.executeQuery("SELECT * FROM test"); // do
																		// something
																		// with
																		// the
																		// connection.
				while (rs.next()) {
					System.out.println(rs.getString(1)); // should print out
															// "1"'
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
		return null;
	}

	public boolean addImageWithId(String photoName, ByteString data, String uuid) {

		System.out.println("INSIDE ADD IMAGE");
		Connection conn = null;
		try {
			conn = cpool.getConnection();
			conn.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);

			String insert = "insert into ImageStore (uuid, name, image) values('"+ uuid + "','" + photoName + "','" + data + "');";
			System.out.println("INSERT QU:" +insert);
			if (conn != null) {
				Statement stmt = conn.createStatement();
				int result = stmt.executeUpdate(insert);
				if (result > 0) {
					logger.debug("Image inserted.");
					return true;
				} else {
					logger.debug("Image insertion failed.");
					return false;
				}
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		} finally {
			if (conn != null) {
				try {
					conn.close();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
		return false;
	}

	@Override
	public ByteString readImage(String uuid) {
		// TODO Auto-generated method stub
		System.out.println("INSIDE READ IMAGE");
		Connection conn = null;
		ByteString image = null;
		try {
			conn = cpool.getConnection();
			conn.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);

			String select = "SELECT * from ImageStore where uuid = '" + uuid + "';";
			
			logger.info("select:>>>>>" +select);
			
			if (conn != null) {
				Statement stmt = conn.createStatement();

				ResultSet rs = stmt.executeQuery(select);
				if (rs == null)
					logger.info("rs is nulllllllll");

				while (rs.next()) {
					logger.info("indid next.....................");
					image = ByteString.copyFrom(rs.getBytes("image"));
				}
				return image;
			}
		} catch (Exception ex) {
			ex.printStackTrace();
			// logger.error("failed/exception on looking up space " + spaceId,
			// ex);
		} finally {
			if (conn != null) {
				try {
					conn.close();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}

		return image;
	}

	@Override
	public boolean deleteImage(String uuid) {
		// TODO Auto-generated method stub
		System.out.println("INSIDE DELETE IMAGE");
		Connection conn = null;

		try {
			conn = cpool.getConnection();
			conn.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);

			String delete = "DELETE FROM ImageStore where uuid = '" + uuid
					+ "';";
			if (conn != null) {

				Statement stmt = conn.createStatement();
				stmt.executeUpdate(delete);

				return true;
			}

		} catch (Exception ex) {
			ex.printStackTrace();
			// logger.error("failed/exception on looking up space " + spaceId,
			// ex);
		} finally {
			if (conn != null) {
				try {
					conn.close();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}

		return false;
	}
}
