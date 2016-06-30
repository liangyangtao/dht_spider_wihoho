package com.unbank.udp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

import org.jboss.netty.bootstrap.ConnectionlessBootstrap;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.channel.socket.DatagramChannelFactory;
import org.jboss.netty.channel.socket.nio.NioDatagramChannelFactory;

import com.dampcake.bencode.Bencode;
import com.unbank.dto.Node;
import com.unbank.utilities.ByteUtil;
import com.unbank.utilities.Utils;

/**
 * Created by wihoho on 19/9/15.
 */

public class DHTServer implements Runnable {

	private LinkedBlockingQueue<Node> queue;

	public int maxGoodNodeCount;
	private Channel channel;
	private String id = Utils.randomId();
	private List<Node> BOOTSTRAP_NODES = new ArrayList<Node>(Arrays.asList(
			new Node(Utils.randomId(), "router.bittorrent.com", 6881),
			new Node(Utils.randomId(), "dht.transmissionbt.com", 6881),
			new Node(Utils.randomId(), "router.utorrent.com", 6881)));

	public DHTServer(int port, int maxGoodNodeCount,
			LinkedBlockingQueue<Node> queue) {
		this.queue = queue;
		DatagramChannelFactory factory = new NioDatagramChannelFactory(
				Executors.newCachedThreadPool());
		ConnectionlessBootstrap b = new ConnectionlessBootstrap(factory);
		b.setPipelineFactory(new ChannelPipelineFactory() {
			public ChannelPipeline getPipeline() throws Exception {
				return Channels.pipeline(new PacketHandler());
			}
		});
		b.setOption("receiveBufferSize", 65536);
		b.setOption("sendBufferSize", 268435456);
		channel = b.bind(new InetSocketAddress(port));
		this.maxGoodNodeCount = maxGoodNodeCount;

	}

	@Override
	public void run() {
		joinDht();
		reJoinDHt();
		//listen
		// autosendfindnode
		while (true) {
			try {
				if (queue.size() > 0) {
					System.out.println("发现新的node ");
					Node node = queue.take();
					// ping(node.getAddress());
					findNode(node);
				}
				Thread.sleep(2000);
			} catch (Exception e) {

				e.printStackTrace();
			}
		}
	}

	/**
	 * 发送请求
	 * 
	 * @param address
	 *            节点地址
	 * @param map
	 *            数据包map
	 */
	public void sendKRPC(InetSocketAddress address, byte[] sendData) {
		try {
			channel.write(ChannelBuffers.copiedBuffer(sendData), address);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void findNode(Node destination) {
		Map<String, Object> map = new HashMap<>();
		map.put("t", Utils.getRandomString(2));
		map.put("y", "q");
		map.put("q", "find_node");

		Map<String, Object> subMap = new HashMap<>();
		subMap.put("id", Utils.getNeighbour(destination.getId(), this.id));
		subMap.put("target", Utils.randomId());
		map.put("a", subMap);

		try {
			byte[] sendData = Utils.enBencode(map);
			InetSocketAddress destinationIp = new InetSocketAddress(
					destination.getAddress(), destination.getPort());
			sendKRPC(destinationIp, sendData);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void joinDht() {
		for (Node node : BOOTSTRAP_NODES) {
			findNode(node);
		}

	}

	public void reJoinDHt() {
		Timer timer = new Timer();
		timer.schedule(new TimerTask() {

			@Override
			public void run() {
				
				if (queue.size() <= 0) {
					joinDht();
				}
			}
		}, 1000, 5000);

	}
	
	public void autoSendFindNode(){
		
	}

	public void onMessage(Map<String, Object> map, Node sourceNode)
			throws IOException {
		if (((String) map.get("y")).isEmpty()) {
			return;
		}
		// y 一个是r

		// 一个是 q

		// handle find_nodes response
		if (map.get("y").equals("r")) {

			Map<String, String> subMap = (Map<String, String>) map.get("r");
			if (subMap.containsKey("nodes")) {
				onFindNodesResponse(map);
			}

		} else if (map.get("y").equals("q")) {
			// handle ping
			switch ((String) map.get("q")) {
			case "ping":
				System.out.println("ping");
				onPing(map, sourceNode);
				break;

			case "get_peers":
				System.out.println("get_peers");
				onGetPeers(map, sourceNode);
				break;

			case "announce_peer":
				System.out.println("announce_peer");
				onAnnouncePeer(map, sourceNode);
				break;

//			case "find_nodes":
//				System.out.println("find_nodes");
//				onFindNodes(map, sourceNode);
//				break;
			default:
				playDead(map, sourceNode);
				break;
			}
		}
	}

	private void playDead(Map<String, Object> map, Node sourceNode) {
		if (map.containsKey("t")) {
			Object tid = map.get("t");
			Map<String, Object> subMap = new HashMap<>();
			subMap.put("t", tid);
			subMap.put("y", "e");
			subMap.put("e", Arrays.asList(202, "Server Error"));
			try {
				byte[] sendData = Utils.enBencode(subMap);
				InetSocketAddress destinationIp = new InetSocketAddress(
						sourceNode.getAddress(), sourceNode.getPort());
				sendKRPC(destinationIp, sendData);
			} catch (Exception e) {
				e.printStackTrace();
			}

		}

	}

	private void notify(Map<String, Object> map, Node sourceNode) {
		Map<String, String> subMap = (Map<String, String>) map.get("a");
		Object tid = map.get("t");
		String nid = subMap.get("id");
		Map<String, Object> resMap = new HashMap<String, Object>();
		resMap.put("t", tid);
		resMap.put("y", "r");
		resMap.put(
				"r",
				new HashMap<String, Object>().put("id",
						Utils.getNeighbour(nid, this.id)));
		try {
			byte[] sendData = Utils.enBencode(resMap);
			InetSocketAddress destinationIp = new InetSocketAddress(
					sourceNode.getAddress(), sourceNode.getPort());
			sendKRPC(destinationIp, sendData);
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	private void onFindNodesResponse(Map<String, Object> map)
			throws UnknownHostException {
		List<Node> decodedNodes = Utils.decodeNodes(((Map<String, String>) (map
				.get("r"))).get("nodes"));
		System.out.println(decodedNodes.size());
		for (Node node : decodedNodes) {
			if (node.isValid()) {
				if (node.getId().length() != 20) {
					continue;
				}
				if (node.getPort() < 1 || node.getPort() > 65535) {
					continue;
				}
				queue.add(node);
			}

		}

	}
//
//	private void onFindNodes(Map<String, Object> map, Node sourceNode) {
//		Map<String, String> subMap = (Map<String, String>) map.get("a");
//
//		Map<String, Object> responseMap = new HashMap<>();
//		responseMap.put("t", map.get("t"));
//		responseMap.put("y", "r");
//		Map<String, String> subMap1 = new HashMap<>();
//		subMap1.put("id", subMap.get("target"));
//		 subMap1.put("nodes", );
//	
//		map.put("t", Utils.getRandomString(2));
//		map.put("y", "q");
//		map.put("q", "find_node");
//
//		Map<String, Object> subMap = new HashMap<>();
//		subMap.put("id", Utils.getNeighbour(destination.getId(), this.id));
//		subMap.put("target", Utils.randomId());
//		map.put("a", subMap);
//
//		try {
//			byte[] sendData = Utils.enBencode(map);
//			InetSocketAddress destinationIp = new InetSocketAddress(
//					sourceNode.getAddress(), sourceNode.getPort());
//			sendKRPC(destinationIp, sendData);
//			// socket.send(sendPacket);
//		} catch (Exception e) {
//			e.printStackTrace();
//		}
//	}

	private void onPing(Map<String, Object> map, Node sourceNode)
			throws IOException {
		Map<String, Object> pong = new HashMap<>();

		pong.put("t", map.get("t"));
		pong.put("y", "r");

		Map<String, String> subMap = new HashMap<>();
		subMap.put("id", this.getId());
		pong.put("r", subMap);

		sendMessage(pong, sourceNode);

	}

	private void sendMessage(Map<String, Object> map, Node targetNode) {
		try {
			byte[] sendData = Utils.enBencode(map);
			InetSocketAddress destinationIp = new InetSocketAddress(
					targetNode.getAddress(), targetNode.getPort());
			sendKRPC(destinationIp, sendData);
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	private void onGetPeers(Map<String, Object> map, Node sourceNode)
			throws IOException {
		if (((String) map.get("q")).isEmpty()
				&& map.get("q").equals("get_peers")) {
			Map<String, String> subMap = (Map<String, String>) map.get("a");
			String infoHash = subMap.get("info_hash");
			Object tid = map.get("t");
			String nid = subMap.get("id");
			// response
			Map<String, Object> responseMap = new HashMap<>();
			responseMap.put("t", tid);
			responseMap.put("y", "r");

			String token = new String(Utils.getByteArray(
					infoHash.getBytes(Bencode.DEFAULT_CHARSET), 0, 1),
					Bencode.DEFAULT_CHARSET);

			Map<String, String> subMap1 = new HashMap<>();
			subMap1.put("id", Utils.randomId());
			subMap1.put("token", token);
			subMap1.put("nodes", "");
			responseMap.put("r", subMap1);

			sendMessage(responseMap, sourceNode);
		}
	}

	private void onAnnouncePeer(Map<String, Object> map, Node sourceNode) {

		if (((String) map.get("q")).isEmpty()
				&& map.get("q").equals("announce_peer")) {
			Map<String, String> subMap = (Map<String, String>) map.get("a");

			String infoHash = subMap.get("info_hash");

			String token = subMap.get("token");

			String nid = subMap.get("id");

			Object tid = map.get("t");
			int port = 0;
			if (infoHash.equals(token)) {
				if (subMap.containsKey("implied_port")) {
					// && subMap.get("implied_port")
					port = sourceNode.getPort();
				} else {
					port = Integer.parseInt(subMap.get("port"));
				}
				if (port > 1 && port < 65535) {

					String infoHashString = ByteUtil.byteArrayToHex(infoHash
							.getBytes());

					String result = "magnet:?xt=urn:btih:" + infoHashString;
					System.out.println("发现种子文件=======" + result);

				}

			}
  
			
			notify(map, sourceNode);
			
		}
	}

	public LinkedBlockingQueue<Node> getQueue() {
		return queue;
	}

	public void setQueue(LinkedBlockingQueue<Node> queue) {
		this.queue = queue;
	}

	public int getMaxGoodNodeCount() {
		return maxGoodNodeCount;
	}

	public void setMaxGoodNodeCount(int maxGoodNodeCount) {
		this.maxGoodNodeCount = maxGoodNodeCount;
	}


	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	private class PacketHandler extends SimpleChannelHandler {

		public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) {

			try {
				byte[] validData = ((ChannelBuffer) e.getMessage()).array();
				Map<String, Object> map = null;
				map = Utils.deBencode(validData);

				InetSocketAddress inetSocketAddress = (InetSocketAddress) e
						.getRemoteAddress();

				Node sourceNode = new Node(Utils.randomId(),
						inetSocketAddress.getHostName(),
						inetSocketAddress.getPort());
				onMessage(map, sourceNode);

			} catch (Exception ex) {
				ex.printStackTrace();
			} finally {
			}
		}

		@Override
		public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
				throws Exception {
			System.out.println(e.getCause().getMessage());
		}

	}

}
