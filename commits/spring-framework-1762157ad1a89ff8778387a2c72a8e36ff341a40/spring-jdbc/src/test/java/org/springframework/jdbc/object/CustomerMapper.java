package org.springframework.jdbc.object;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.Customer;

import java.sql.ResultSet;
import java.sql.SQLException;

public class CustomerMapper implements RowMapper<Customer> {

	private static final String[] COLUMN_NAMES = new String[] {"id", "forename"};

	public Customer mapRow(ResultSet rs, int rownum) throws SQLException {
		Customer cust = new Customer();
		cust.setId(rs.getInt(COLUMN_NAMES[0]));
		cust.setForename(rs.getString(COLUMN_NAMES[1]));
		return cust;
	}

}
