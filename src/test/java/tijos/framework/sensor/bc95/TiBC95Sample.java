package tijos.framework.sensor.bc95;

import java.io.IOException;

import tijos.framework.devicecenter.TiUART;
import tijos.framework.util.Delay;

/**
 * BC95 NB-IOT Model Sample  
 *
 */
public class TiBC95Sample
{
	public static void main(String[] args) {
		System.out.println("Hello World!");

		try {
			TiUART uart = TiUART.open(1);

			uart.setWorkParameters(8, 1, TiUART.PARITY_NONE, 9600);

			TiBC95 bc95 = new TiBC95(uart);

			System.out.println("Start...");
			//查询模块射频功能状态
			if (!bc95.isMTOn()) {
				System.out.println("Turn ON MT ...");
				bc95.turnOnMT();
				while(!bc95.isMTOn()) {
					Delay.msDelay(2000);
				}
			}
			
			//查询网络是否激活
			if(!bc95.isNetworkActived()) {
				System.out.println("Active network ...");
				bc95.activeNetwork();
				Delay.msDelay(1000);
				while (!bc95.isNetworkActived()) {
					Delay.msDelay(1000);
				}
			}
			

			System.out.println(" IMSI : " + bc95.getIMSI());
			System.out.println(" IMEI : " + bc95.getIMEI());
			System.out.println(" RSSI : " + bc95.getRSSI());

			System.out.println(" Is Actived :" + bc95.isNetworkActived());
			System.out.println(" Is registered : " + bc95.isNetworkRegistred());

			System.out.println("Connection Status : " + bc95.getNetworkStatus());
			
			System.out.println("IP Address " + bc95.getIPAddress());
			System.out.println("Date time "  + bc95.getDateTime());

			//COAP Server IP which is bound to the SIM card
			String serverIp = "115.29.240.46";

			
			try {
				bc95.ping(serverIp);
			}
			catch(IOException ex) {
				System.out.println("Failed to pin " + serverIp);
			}

			//COAP data transmission
			byte[] data = "This is a test".getBytes();
			bc95.setCDPServer(serverIp, 5683);
			bc95.enableMsgNotification(true);
			bc95.coapSend(data);
			
			System.out.println("Done");

		} catch (IOException ex) {
			ex.printStackTrace();
		}

	}
}
