/*
 * Created on Oct 27, 2004
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package no.ntnu.fp.net.co;

import java.io.EOFException;
import java.io.IOException;

import no.ntnu.fp.net.admin.Log;
import no.ntnu.fp.net.co.Connection;

/**
 * Simplest possible test application, server part.
 *
 * @author seb, steinjak
 *
 */
public class StressTest_Server {

  /**
   * Empty.
   */
  public StressTest_Server() {
  }

  /**
   * Program Entry Point.
   */
  public static void main (String args[]){

    // Create log
    Log log = new Log();
    log.setLogName("Server");

    // server connection instance, listen on port 5555
    Connection server = new ConnectionImpl(5555);
    // each new connection lives in its own instance
    Connection conn;
    int rec = 0;
    try {
      conn = server.accept();

      try {
	while (rec < 20) {
	  String msg = conn.receive();
	  Log.writeToLog("Message got through to server: " + msg,
			 "TestServer");
	  rec ++;
	}
      } catch (EOFException e){
	Log.writeToLog("Got close request (EOFException), closing.",
		       "TestServer");
	conn.close();
      }
      
      for(int i = 0; i<20; i++){
    	  conn.send("AnswerPacket " + i);    	  
      }
      
      try{
    	  while(true){
    		  String msg = conn.receive();
    	  }
      }
      catch (EOFException e){
    	  conn.close();
      }

      System.out.println("SERVER TEST FINISHED");
      Log.writeToLog("TEST SERVER FINISHED","TestServer");
    }
    catch (IOException e){
    	System.out.println("Connection Fails ServerSide ######################################");
      e.printStackTrace();
    }
  }
}
