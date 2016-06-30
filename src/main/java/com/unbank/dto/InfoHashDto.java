package com.unbank.dto;

import java.util.UUID;

/**
 * Created by wihoho on 2/10/15.
 */


public class InfoHashDto {

    private UUID _id;
    private String infoHash;
    private String ipAddress;
	public UUID get_id() {
		return _id;
	}
	public void set_id(UUID _id) {
		this._id = _id;
	}
	public String getInfoHash() {
		return infoHash;
	}
	public void setInfoHash(String infoHash) {
		this.infoHash = infoHash;
	}
	public String getIpAddress() {
		return ipAddress;
	}
	public void setIpAddress(String ipAddress) {
		this.ipAddress = ipAddress;
	}

    
    
}
