package tijos.framework.sensor.bc95;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import java.util.Date;
import tijos.framework.devicecenter.TiUART;
import tijos.framework.util.Delay;
import tijos.framework.util.Formatter;

/**
 * Quectel BC95 NB-IOT module driver for TiJOS , refer to http://www.quectel.com/product/bc95.htm for more information
 * Note that: NB-IOT module need to bind a server IP for data transportation, 
 * please confirm with the NB-IOT provider before testing
 */
public class TiBC95 {

	// IO stream for UART
	InputStream input;
	OutputStream output;
	
	TiUART uart;
	

	/**
	 * Initialize IO stream for UART
	 * 
	 * @param uart
	 *            TiUART object
	 */
	public TiBC95(TiUART uart) {
		this.uart = uart;
		this.input = new BufferedInputStream(new TiUartInputStream(uart), 256);
		this.output = new TiUartOutputStream(uart);

	}

	/**
	 * 查询模块射频功能状态
	 * @return true 射频已打开 false 射频未打开
	 * @throws IOException
	 */
	public boolean isMTOn() throws IOException {

		writeATCmd("AT+CFUN?");
		String resp = readATRespWithData();
		if (resp.equals("+CFUN:1"))
			return true;

		return false;
	}

	/**
	 * 打开模块射频功能
	 * @throws IOException
	 */
	public void turnOnMT() throws IOException {
		writeATCmd("AT+CFUN=1");
		Delay.msDelay(4000); //4 seconds delay 
		readATResp();
	}

	/**
	 * 关闭模块射频功能
	 * @throws IOException
	 */
	public void turnOffMT() throws IOException {
		writeATCmd("AT+CFUN=0");
		readATResp();
	}

	/**
	 * 查询 IMSI 码 
	 * IMSI 是国际移动用户识别码，International Mobile Subscriber Identification Number 的缩写 
	 * @return IMSI 
	 * @throws IOException
	 */
	public String getIMSI() throws IOException {
		writeATCmd("AT+CIMI");
		return readATRespWithData();
	}

	/**
	 * 查询模块 IMEI,  国际移动设备识别码International Mobile Equipment Identity
	 * @return
	 * @throws IOException
	 */
	public String getIMEI() throws IOException {
		writeATCmd("AT+CGSN=1");
		String resp = readATRespWithData();

		int end = resp.lastIndexOf(':');
		if (end < 0) {
			throw new IOException("Wrong response");
		}

		String imei = resp.substring(end + 1);
		return imei;
	}

	/**
	 * 查询模块信号 
	 * @return 0 - 表示网络未知，或者网络未附着
	 * @throws IOException
	 */
	public int getRSSI() throws IOException {
		writeATCmd("AT+CSQ");
		String resp = readATRespWithData();

		int begin = resp.indexOf(':');
		int end = resp.lastIndexOf(',');

		if (begin < 0 || end < 0 || begin >= end)
			throw new IOException("Wrong response");

		String rssi = resp.substring(begin + 1, end);

		int r = Integer.parseInt(rssi);
		if(r == 99) {//no signl
			r = 0;
		}
		
		return r;
	}

	/**
	 * 查询网络是否激活
	 * @return
	 * @throws IOException
	 */
	public boolean isNetworkActived() throws IOException {
		writeATCmd("AT+CGATT?");
		String resp = readATRespWithData();
		if (resp.equals("+CGATT:1"))
			return true;
		return false;

	}

	/**
	 * 激活网络
	 * @throws IOException
	 */
	public void activeNetwork() throws IOException {
		writeATCmd("AT+CGATT=1");
		readATResp();
	}

	/**
	 * 查询网络是否注册
	 * @return
	 * @throws IOException
	 */
	public boolean isNetworkRegistred() throws IOException {
		writeATCmd("AT+CEREG?");
		String resp = readATRespWithData();

		int begin = resp.lastIndexOf(',');
		if (begin < 0)
			throw new IOException("Wrong response");

		String stat = resp.substring(begin + 1);

		int s = Integer.parseInt(stat);
		return s > 0? true : false;
	}

	/**
	 * 查询当前网络连接状态 
	 * @return 0 处于 IDLE 状态  1 处于已连接状态  
	 * 			当处于 IDLE 状态时，只要发送数据，就会变成已连接状态
	 * @throws IOException
	 */
	public int getNetworkStatus() throws IOException {
		writeATCmd("AT+CSCON?");
		String resp = readATRespWithData();

		int begin = resp.lastIndexOf(',');
		if (begin < 0)
			throw new IOException("Wrong response");

		String stat = resp.substring(begin + 1);

		return Integer.parseInt(stat);
	}
	
	/**
	 * 获取设备IP地址
	 * @return
	 * @throws IOException
	 */
	public String getIPAddress() throws IOException {
		writeATCmd("AT+CGPADDR=0");
		String resp = readATRespWithData();
		
		if(!resp.startsWith("+CGPADDR"))
			throw new IOException("Failed to get IP address");
		
		int pos = resp.lastIndexOf(',');
		if(pos < 0)
			return "";
		
		return resp.substring(pos + 1);
	}

	/**
	 * 设置自动入网
	 * @param auto true - 重启后自动入网, false - 模块重启后不会自动连接到网络
	 * @throws IOException
	 */
	public void configAutoConnect(boolean auto) throws IOException {
		if (auto)
			writeATCmd("AT+NCONFIG=AUTOCONNECT,TRUE");
		else
			writeATCmd("AT+NCONFIG=AUTOCONNECT,FALSE");

		readATResp();
	}

	/**
	 * 测试 IP 地址是否可用 
	 * @param ip
	 * @return
	 * @throws IOException
	 */
	public boolean ping(String ip) throws IOException {

		writeATCmd("AT+NPING=" + ip);

		String result = "";
		readLineTimeout(5000);
		String ret = readLine();
		readLine();

		result = readLine();

		if (!ret.equals("OK")) {
			throw new IOException("AT Error - " + ret);
		}

		if (result.startsWith("+NPING:"))
			return true;

		return false;
	}

	/**
	 * 创建 UDP 通信 Socket
	 * @param listenPort 本地监听端口
	 * @return socket id
	 * @throws IOException
	 */
	public int createUDPSocket(int listenPort) throws IOException {
		writeATCmd("AT+NSOCR=DGRAM,17," + listenPort + ",1");
		String resp = readATRespWithData();

		return Integer.parseInt(resp);
	}

	/**
	 * 关闭socket
	 * @param socketId
	 * @throws IOException
	 */
	public void closeUDPSocket(int socketId) throws IOException {
		writeATCmd("AT+NSOCL=" + socketId);

		this.readATResp();
	}

	/**
	 * 发送UDP数据包到远程服务器
	 * @param socketId  
	 * @param remoteAddr  远程服务器IP
	 * @param remotePort  远程服务器 端口
	 * @param data  将发送的数据
	 * @return  成送数据长度
	 * @throws IOException
	 */
	public int udpSend(int socketId, String remoteAddr, int remotePort, byte[] data) throws IOException {
		writeATCmd("AT+NSOST=" + socketId + "," + remoteAddr + "," + remotePort + "," + data.length + ","
				+ Formatter.toHexString(data));

		String resp = readATRespWithData();
		if (socketId != resp.charAt(0) - '0')
			throw new IOException("Wrong socket id");

		return Integer.parseInt(resp.substring(2));
	}

	/**
	 * 接收UDP数据 
	 * 注意： 由于NB-IOT及UDP的特点， 下行数据需要要收到上行数据后立刻下发, 同时不保证数据能够到达, 在实际 应用中需要根据实际 情况进行处理
	 * @return 收到的UDP数据 
	 * @throws IOException
	 */
	public byte[] udpReceive() throws IOException {
		this.readLine();
		String resp = this.readLine();

		int begin = resp.indexOf(':');
		int end = resp.lastIndexOf(',');

		if (begin < 0 || end < 0 || begin > end)
			throw new IOException("Wrong response");

		int socketId = Integer.parseInt(resp.substring(begin + 1, end));
		int length = Integer.parseInt(resp.substring(end + 1));

		writeATCmd("AT+NSORF=" + socketId + "," + length);
		resp = readATRespWithData();

		begin = resp.indexOf(',');
		socketId = Integer.parseInt(resp.substring(0, begin));

		int ipPos = resp.indexOf(',', begin + 1);
		String remoteAddr = resp.substring(begin + 1, ipPos);

		int portPos = resp.indexOf(',', ipPos + 1);
		int port = Integer.parseInt(resp.substring(ipPos + 1, portPos));

		int lenPos = resp.indexOf(',', portPos + 1);
		length = Integer.parseInt(resp.substring(portPos + 1, lenPos));

		int dataPos = resp.indexOf(',', lenPos + 1);
		String data = resp.substring(lenPos + 1, dataPos);

		int left = Integer.parseInt(resp.substring(dataPos + 1));

		return this.hexStringToByte(data);

	}

	/**
	 * 设备COAP/CDP 服务器IP及端口  
	 * @param ip
	 * @param port
	 * @throws IOException
	 */
	public void setCDPServer(String ip, int port) throws IOException {

		writeATCmd("AT+NCDP=" + ip + "," + port);
		readATResp();
	}

	/**
	 * 启用发送和新消息通知
	 * @param enable true - 开启 false- 关闭
	 * @throws IOException
	 */
	public void enableMsgNotification(boolean enable) throws IOException {
		if (enable) {
			writeATCmd("AT+NSMI=1");
		} else {
			writeATCmd("AT+NSMI=0");
		}

		this.readATResp();

	}

	/**
	 * 通过COAP向服务器发送数据 
	 * @param data 待发送数据 
	 * @throws IOException
	 */
	public void coapSend(byte[] data) throws IOException {

		writeATCmd("AT+NMGS=" + data.length + "," + Formatter.toHexString(data));

		readATResp();

		readLine();
		String result = readLine();
		if(!result.equals("+NSMI:SENT")) {
			throw new IOException("Failed: " + result);
		}
	}

	/**
	 * 接收COAP数据 
	 * 注意： 由于NB-IOT的特点， 下行数据需要要收到上行数据后立刻下发, 同时不保证数据能够到达, 在实际 应用中需要根据实际 情况进行处理
	 * @return
	 * @throws IOException
	 */
	public byte[] coapReceive() throws IOException {

		readLine();
		String data = readLine();
		if(data.length() == 0)
			return null;
		
		int pos = data.lastIndexOf(',');
		if(pos > 0)
			return this.hexStringToByte(data.substring(pos + 1));
		
		return null;
	}
	
	/**
	 * Get date time from the network
	 * @return
	 * @throws IOException
	 */
	public Date getDateTime() throws IOException {
		writeATCmd("AT+CCLK?");
		
		String data = this.readATRespWithData();
		int begin = data.indexOf(':');

		int yearPos = data.indexOf('/', begin + 1);
		int year = Integer.parseInt(data.substring(begin + 1, yearPos));
		
		int monPos = data.indexOf('/', yearPos + 1);
		int month = Integer.parseInt(data.substring(yearPos + 1, monPos));
		
		int dayPos = data.indexOf(',', yearPos + 1);
		int day = Integer.parseInt(data.substring(monPos + 1, dayPos));

		int hourPos = data.indexOf(':', dayPos + 1);
		int hours = Integer.parseInt(data.substring(dayPos + 1, hourPos));
		
		int minPos = data.indexOf(':', hourPos +1);
		int minutes = Integer.parseInt(data.substring(hourPos + 1, minPos));
	
		int secondPos = data.indexOf('+', minPos +1);
		int seconds = Integer.parseInt(data.substring(minPos + 1, secondPos));
	
		return new Date(year + 100, month - 1, day, hours, minutes, seconds);
		
	}

	/**
	 * Send AT command to device
	 * @param cmd
	 * @throws IOException
	 */
	private void writeATCmd(String cmd) throws IOException {
		
		clearInput();
		output.write((cmd + "\r\n").getBytes());
	}

	/**
	 * AT response parser
	 * @return
	 * @throws IOException
	 */
	private String readATResp() throws IOException {
		int t = 0;
		String result = "";
		readLine();
		String ret = readLine();

		if (!ret.equals("OK")) {
			throw new IOException("AT Error - " + ret);
		}

		return result;

	}

	private String readATRespWithData() throws IOException {
		int t = 0;
		String result = "";
		readLine();
		result = readLine();
		readLine();

		String ret = readLine();

		if (!ret.equals("OK")) {
			throw new IOException("AT Error - " + ret);
		}

		return result;

	}

	private String readATRespWithData2() throws IOException {
		int t = 0;
		String result = "";
		readLine();
		String ret = readLine();
		readLine();

		result = readLine();

		if (!ret.equals("OK")) {
			throw new IOException("AT Error - " + ret);
		}

		return result;

	}

	private String readLine() throws IOException {
		StringBuilder sb = new StringBuilder(32);

		int timeout = 4000;
		while ((timeout -= 20) > 0) {
			if (input.available() < 2) {
				Delay.msDelay(10);
				continue;
			}

			int val = input.read();
			if (val == 0x0D) {
				val = input.read(); // 0x0a
				break;
			}

			sb.append((char) val);
		}
		return sb.toString();
	}

	private String readLineTimeout(int timeOut) throws IOException {
		StringBuilder sb = new StringBuilder(32);

		while ((timeOut -= 20) > 0) {
			if (input.available() < 2) {
				Delay.msDelay(10);
				continue;
			}

			int val = input.read();
			if (val == 0x0D) {
				val = input.read(); // 0x0a
				break;
			}

			sb.append((char) val);
		}
		return sb.toString();
	}
	
	private void clearInput() throws IOException{
		
		while(this.input.read() > 0);
		this.uart.clear(3); //clear both input and output buffer
		
	}

	private byte[] hexStringToByte(String str) {
		if (str == null) {
			return null;
		}
		if (str.length() == 0) {
			return new byte[0];
		}
		byte[] byteArray = new byte[str.length() / 2];
		for (int i = 0; i < byteArray.length; i++) {
			String subStr = str.substring(2 * i, 2 * i + 2);
			byteArray[i] = ((byte) Integer.parseInt(subStr, 16));
		}
		return byteArray;
	}


}
