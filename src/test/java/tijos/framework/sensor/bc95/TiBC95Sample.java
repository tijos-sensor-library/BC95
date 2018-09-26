package tijos.framework.sensor.bc95;

import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

import tijos.framework.devicecenter.TiUART;
import tijos.framework.util.Delay;

/**
 * BC95 NB-IOT Model Sample  
 *
 */

class BC95EventListener implements IDeviceEventListener
{

	@Override
	public void onCoapDataArrived(byte []message) {
		System.out.println("onCoapDataArrived");
	}
	
	@Override
	public void onUDPDataArrived(byte [] packet) {
		System.out.println("onUDPDataArrived");
	}
}

class DataAcquireTask extends TimerTask {  
	  
	TiBC95 bc95;
	int counter = 0;

	public DataAcquireTask(TiBC95 bc95) {
		this.bc95 = bc95;
	}
	
    @Override  
    public void run() {  
        System.out.println("report to OC platform");  

		//COAP data transmission 
		byte[] data = new byte[5];
		
		//通讯结构需要与电信平台中定义的Profile和插件一致， 具体请参考电信平台相关文档
		counter ++;
		data[0] = 0x00;
		data[1] = (byte)counter;
		data[2] = (byte)(counter + 1);
		data[3] = 1;
		data[4] = 1;
		
		try {
			bc95.coapSend(data);
		}
		catch(IOException ex) {
			ex.printStackTrace();
		}
		
    }  
  
}  

public class TiBC95Sample
{
	public static void main(String[] args) {
		System.out.println("Hello World!");

		try {
			TiUART uart = TiUART.open(2);
			
			uart.setWorkParameters(8, 1, TiUART.PARITY_NONE, 9600);

			
			TiBC95 bc95 = new TiBC95(uart);
			bc95.setEventListener(new BC95EventListener());

			System.out.println("Start...");
			//查询模块射频功能状态
			if (!bc95.isMTOn()) {
				System.out.println("Turn ON MT ...");
				bc95.turnOnMT();
				int counter = 0;
				while(!bc95.isMTOn()) {
					Delay.msDelay(2000);					
					if(counter++ > 10) {
						bc95.turnOnMT();
						counter = 0;
					}
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
			
			
			String [] status = bc95.queryUEStatistics();
			

			System.out.println(" IMSI : " + bc95.getIMSI());
			System.out.println(" IMEI : " + bc95.getIMEI());
			System.out.println(" RSSI : " + bc95.getRSSI());

			System.out.println(" Is Actived :" + bc95.isNetworkActived());
			System.out.println(" Is registered : " + bc95.isNetworkRegistred());

			System.out.println("Connection Status : " + bc95.getNetworkStatus());
			
			System.out.println("IP Address " + bc95.getIPAddress());
			System.out.println("Date time "  + bc95.getDateTime());

			//电信物联网平台分配的IP, 请换成实际的服务器IP
			String serverIp = "180.101.147.115";

			bc95.setCDPServer(serverIp, 5683);
			bc95.enableMsgNotification(true);
			bc95.enableNewArriveMessage();
			
			java.util.Timer timer = new Timer(true); 
			timer.schedule(new DataAcquireTask(bc95), 1000, 1 * 60 * 1000); //每1分钟执行

			while(true) {
				Delay.msDelay(10000);
			}

		} catch (IOException ex) {
			ex.printStackTrace();
		}

	}
}
