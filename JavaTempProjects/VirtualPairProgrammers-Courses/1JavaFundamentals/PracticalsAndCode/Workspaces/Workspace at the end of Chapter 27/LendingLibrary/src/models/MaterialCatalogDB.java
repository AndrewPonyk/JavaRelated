package models;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.TreeMap;

public class MaterialCatalogDB implements MaterialCatalogInterface {

	public MaterialCatalogDB() {
		try {
			Class.forName("org.apache.derby.jdbc.ClientDriver");
		}
		catch (ClassNotFoundException e) {
			throw new RuntimeException(e);
		}
	}
	
	@Override
	public void addMaterial(Material newMaterial) {

		Connection conn = null;
		PreparedStatement stm = null;

		try {

			try {
				conn = DriverManager.getConnection("jdbc:derby://localhost/library");

				if (newMaterial instanceof Book) {
					Book newBook = (Book)newMaterial;

					String sql = "insert into materials (barcode,title,author,isbn,numberofpages,branch,type) values (?,?,?,?,?,?,'BOOK')";
					stm = conn.prepareStatement(sql);
					stm.setString(1,newBook.getID());
					stm.setString(2, newBook.getTitle());
					stm.setString(3, newBook.getAuthor());
					stm.setString(4, newBook.getIsbn());
					stm.setInt(5, newBook.getNumberOfPages());
					stm.setString(6,"Anytown Branch");

				}
				else if (newMaterial instanceof DVD) {
					DVD newDVD = (DVD)newMaterial;
					String sql = "insert into materials (barcode,title,catalognumber,runningtime,licenced,branch,type) values (?,?,?,?,?,?,'DVD')";
					stm = conn.prepareStatement(sql);
					stm.setString(1,newDVD.getID());
					stm.setString(2, newDVD.getTitle());
					stm.setString(3, newDVD.getCatalogNumber());
					stm.setInt(4, newDVD.getRunningTime());
					stm.setBoolean(5, newDVD.getLicenced());
					stm.setString(6,"Anytown Branch");
				}

				int results = stm.executeUpdate();
				System.out.println("Records added: " + results);

			}
			finally {

				if (stm != null) stm.close();
				if (conn != null) conn.close();
			}
		}

		
		catch (SQLException e) {

			System.out.println("Something went wrong");
			System.out.println(e);
		}

	}

	@Override
	public TreeMap<String, Material> getMaterialMap() {

		Connection conn = null;
		Statement stm = null;
		ResultSet rs = null;
		TreeMap<String, Material> allMaterials = new TreeMap<String,Material>();

		try {

			try {
				
				conn = DriverManager.getConnection("jdbc:derby://localhost/library");
				stm = conn.createStatement();
				rs = stm.executeQuery("select * from materials");

				while (rs.next()) {
					String materialType = rs.getString("type");
					if (materialType.equalsIgnoreCase("BOOK")) {
						Book newBook = new Book(rs.getString("barcode"),rs.getString("title"),rs.getString("author"),
								rs.getString("isbn"),rs.getString("branch"), rs.getInt("numberofpages"));
						allMaterials.put(rs.getString("barcode"), newBook);
					}
					else if (materialType.equalsIgnoreCase("DVD")) {
						DVD newDVD = new DVD(rs.getString("barcode"),rs.getString("title"),rs.getString("branch"),
								rs.getString("director"),rs.getString("catalognumber"), rs.getInt("runningtime"));
						allMaterials.put(rs.getString("barcode"), newDVD);
					}


				}
				return allMaterials;

			}
			finally {
				if (rs != null) rs.close();
				if (stm != null) stm.close();
				if (conn != null) {
					conn.close();
					System.out.println("Connection was closed");
				}
				System.out.println("Finally was run");
			}
		}

	
		catch (SQLException e) {
			throw new RuntimeException(e);
		}


	}

	@Override
	public Material findMaterial(String title) throws MaterialNotFoundException {
		Connection conn = null;
		Statement stm = null;
		ResultSet rs = null;

		try {

			try {
				conn = DriverManager.getConnection("jdbc:derby://localhost/library");
				stm = conn.createStatement();
				rs = stm.executeQuery("select * from materials where title like '%" + title + "%'");

				if (rs.next()) {
					String materialType = rs.getString("type");
					if (materialType.equalsIgnoreCase("BOOK")) {
						Book newBook = new Book(rs.getString("barcode"),rs.getString("title"),rs.getString("author"),
								rs.getString("isbn"),rs.getString("branch"), rs.getInt("numberofpages"));
						return newBook;
					}
					else if (materialType.equalsIgnoreCase("DVD")) {
						DVD newDVD = new DVD(rs.getString("barcode"),rs.getString("title"),rs.getString("branch"),
								rs.getString("director"),rs.getString("catalognumber"), rs.getInt("runningtime"));
						return newDVD;
					}
					else
					{
						throw new MaterialNotFoundException();
					}
				}
				else {
					throw new MaterialNotFoundException();
				}

			}
			finally {
				if (rs != null) rs.close();
				if (stm != null) stm.close();
				if (conn != null) {
					conn.close();
					System.out.println("Connection was closed");
				}
				System.out.println("Finally was run");
			}
		}


		catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public int getNumberOfMaterials() {
		Connection conn = null;
		Statement stm = null;
		ResultSet rs = null;

		try {

			try {
				conn = DriverManager.getConnection("jdbc:derby://localhost/library");
				stm = conn.createStatement();
				rs = stm.executeQuery("select count(id) from materials");

				rs.next();
				return rs.getInt(1);

			}
			finally {
				if (rs != null) rs.close();
				if (stm != null) stm.close();
				if (conn != null) {
					conn.close();
					System.out.println("Connection was closed");
				}
				System.out.println("Finally was run");
			}
		}

		catch (SQLException e) {
			throw new RuntimeException(e);
		}

	}


}
