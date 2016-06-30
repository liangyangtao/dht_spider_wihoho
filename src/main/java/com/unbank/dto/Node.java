package com.unbank.dto;

import com.google.common.base.Strings;

/**
 * Created by wihoho on 19/9/15.
 */

public class Node {
	private String id;
	private String address;
	private int port;

	public Node(String id, String address, int port) {
		super();
		this.id = id;
		this.address = address;
		this.port = port;
	}

	public boolean isValid() {
		if (Strings.isNullOrEmpty(address))
			return false;

		if (port < 1 || port > 65535)
			return false;

		return true;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getAddress() {
		return address;
	}

	public void setAddress(String address) {
		this.address = address;
	}

	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}

	@Override
	public String toString() {
		return "Node [id=" + id + ", address=" + address + ", port=" + port
				+ "]";
	}

}
