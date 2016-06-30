package com.unbank;

import java.util.concurrent.LinkedBlockingQueue;

import com.unbank.dto.Node;
import com.unbank.udp.DHTServer;

/**
 * Created by wihoho on 20/9/15.
 */
public class Crawler {
	public static void main(String[] args) {
		LinkedBlockingQueue<Node> queue = new LinkedBlockingQueue<Node>();
		DHTServer server = new DHTServer( 6882, 88800, queue);
		new Thread(server).start();
	}
}
