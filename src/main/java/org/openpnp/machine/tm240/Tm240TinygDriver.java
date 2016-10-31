package org.openpnp.machine.tm240;
import java.awt.Color;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.swing.Action;

import org.openpnp.gui.support.PropertySheetWizardAdapter;
import org.openpnp.gui.support.Wizard;
import org.openpnp.machine.reference.ReferenceActuator;
import org.openpnp.machine.reference.ReferenceHead;
import org.openpnp.machine.reference.ReferenceHeadMountable;
import org.openpnp.machine.reference.ReferenceNozzle;
import org.openpnp.machine.reference.driver.AbstractSerialPortDriver;
import org.openpnp.machine.reference.driver.GcodeDriver.Axis;
import org.openpnp.model.Configuration;
import org.openpnp.model.LengthUnit;
import org.openpnp.model.Location;
import org.openpnp.model.Part;
import org.openpnp.spi.Camera;
import org.openpnp.spi.Nozzle;
import org.openpnp.spi.PropertySheetHolder;
import org.openpnp.util.VisionUtils;
import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.ElementList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class Tm240TinygDriver extends AbstractSerialPortDriver implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(Tm240TinygDriver.class);
    
    static final double SAFE_QUICK_Z=1.5;
    
    
	static String patternMachineReady = ".*(?:Ready|Stop).*";
	static String patternResponseReceived = ".*ok.*";
	
	protected static int NBER_INPUT_NEEDLE=3;
	protected static int NBER_INPUT_VACCUM1=2;
	protected static int NBER_INPUT_VACCUM2=1;
	
	
	protected static String HOMEZ_CMD[] = { "G1 F6000 Z200", "G28.3 Z57", "G1 F1000 Z0",
	};
	protected static String HOMEXY_CMD[] = { "$xzb=2", "$yzb=2", "G28.2 X0 Y0",
	};
	protected static String CONNECT_CMD[] = { "$tv=1", "$clear", "$qv=0", "$sv=1", "$ej=2", "$si=10000", "$do8mo=0", "G21", "$sl=0", "$cjm=500", "$cvm=40000",
			"G90", };
	protected static String GETPOS_CMD[] = { "$sr" };
	
	protected static String RESET_CARD[] = { "\u0018" };

	protected static String HOMEAB_CMD[] = { "G0 A0 B0"  };
	
	protected static String REINIT_CMD[] = { "$clear"  };
	
	
	
	
	
    @ElementList(required = false)
    protected List<Axis> axes = new ArrayList<>();

    

    @Attribute(required = false)
    protected double feedRateMmPerMinute = 80000;
    
    @Attribute(required = false)
    protected double feedRateMmPerMinuteRotation = 80000;

    @Attribute(required = false)
    protected double feedRateMmPerMinuteZ = 1000;
    

    @Attribute(required = false)
    private double zCamRadius = 17;

    @Attribute(required = false)
    private double zCamWheelRadius = 0;

    @Attribute(required = false)
    private double zGap = 2;

    @Attribute(required = false)
    private boolean homeZ = false;

    protected int    ledColor;
    protected double x, y, zA, c, c2;
    protected Thread readerThread;
    private boolean disconnectRequested;
    private Object commandLock = new Object();
    public boolean connected;
    protected LinkedBlockingQueue<String> responseQueue = new LinkedBlockingQueue<>();
    private boolean n1Picked, n2Picked;
    private Location limitMachine=new Location(LengthUnit.Millimeters,-1000,-1000,0,0);
    
    
    private void moveAtZ(Nozzle nozzle, double z) throws Exception
    {
    // Transformer une descente Z en degres (principe de biellette)	
    double a = Math.toDegrees(Math.asin((z - zCamWheelRadius - zGap) / zCamRadius));
    // Distance de 1mm de moins 
    double a_1mm = Math.toDegrees(Math.asin((z-SAFE_QUICK_Z - zCamWheelRadius - zGap) / zCamRadius));
    logger.debug("nozzle {} {} {} {}", z, zCamRadius, a, a_1mm);
    if (getNozzleIndex(nozzle) == 1) 
    	{
    	// Symétrie degrés
        a = -a;
        a_1mm=-a_1mm;
    	}
    }
    
    

    @Override
    public void setEnabled(boolean enabled) throws Exception {
        if (enabled && !connected) {
            connect();
        }
        if (connected) {
            if (enabled) {
            	try
            		{
            		sendCommand(REINIT_CMD, 600);
            		}
            	finally
            		{
            		
            		}
                n1Vacuum(false);
                n1Exhaust(false);
                n2Vacuum(false);
                n2Exhaust(false);
                led(true);
            }
            else {
            	try
        		{
        		sendCommand(REINIT_CMD, 600);
        		}
            	catch(Exception e)
            	{
            		sendCommand(RESET_CARD,0);
            		return;
            	}
                n1Vacuum(false);
                n1Exhaust(false);
                n2Vacuum(false);
                n2Exhaust(false);
                led(false);
                pump(false);
            }
        }
    }
    
    
    private Location tuningHome(ReferenceHead head,Location partFiducial) throws Exception {
        /*
         * The head camera for nozzle-1 should now be (if everything has homed correctly) directly
         * above the homing pin in the machine bed, use the head camera scan for this and make sure
         * this is exactly central - otherwise we move the camera until it is, and then reset all
         * the axis back to 0,0,0,0 as this is calibrated home.
         */
        Part homePart = Configuration.get().getPart("FIDUCIAL-HOME");
        if (homePart != null) {
        	//locationFiducial = Configuration.get().getMachine().getFiducialLocator().
            Location correctLocation = Configuration.get().getMachine().getFiducialLocator().getHomeFiducialLocation(partFiducial, homePart);
            getCurrentPosition();
            return correctLocation;

        }
		return null;
    	
    }
    

    @Override
    public void home(ReferenceHead head) throws Exception {
    	
    	
    	waitMachineReady(sendCommand(HOMEAB_CMD, 1000),10*1000);
       waitMachineReady(sendCommand(HOMEXY_CMD, 1000),30*1000);
        if (homeZ) {
            // Home Z
            waitMachineReady(sendCommand(HOMEZ_CMD, 1000),10*1000);
        }
        else {
            // We "home" Z by turning off the steppers, allowing the
            // spring to pull the nozzle back up to home.
            // And call that zero
            // And wait a tick just to let things settle down
            Thread.sleep(250);
        }
        
        // Update position
        getCurrentPosition();
        limitMachine = new Location(LengthUnit.Millimeters,-999.9,-9999.9,0,0);
        // Home X and Y
    	Location tmp = new Location(LengthUnit.Millimeters, 69,39, 0.0, 0.0);   //VENOM
    	
    	
    	
    	
    	Location result = tuningHome(head,tmp);
    	
    	
    	
    	if (result==null)
    		{
    		throw new Exception("Homing fiducial is not detected");
    		}
    	if (result.getLinearDistanceTo(tmp)>2d)
    		{
    		throw new Exception("Imprecision in homing : ecart"+result.getLinearDistanceTo(tmp));
    		}

    	Location officialLocationPosition =head.getDefaultCamera().getLocation().subtract(head.getDefaultNozzle().getLocation());
        
    	limitMachine = new Location(LengthUnit.Millimeters,-x,-y,0,0).add(officialLocationPosition).add(new Location(LengthUnit.Millimeters,2,2,0,0));
    	setPositionXY(officialLocationPosition);
        getCurrentPosition();
    	
        }
    


    @Override
    public void actuate(ReferenceActuator actuator, boolean on) throws Exception {
         if (actuator.getIndex() == 0) {
        	 sendCommand(on ? "$out3=1" : "$out3=0");
        	 Thread.sleep(600);
        	 if (getStateOfNeedle()==!on)
        	 	{
        		throw new Exception("Needle is blocked"); 
        	 	}
         }
         if (actuator.getIndex() == 1) {
        	 sendCommand("G91");
        	 sendCommand("G0 C5");
        	 sendCommand("G90");
         }
    }



    @Override
    public void actuate(ReferenceActuator actuator, double value) throws Exception 
    	{
    	
    	}


    @Override
    public Location getLocation(ReferenceHeadMountable hm) {
        Location result = null;
        if (hm instanceof ReferenceNozzle) {
            ReferenceNozzle nozzle = (ReferenceNozzle) hm;
            double z = this.zA;
            int nozzleIndex = getNozzleIndex(nozzle);
            result =  new Location(LengthUnit.Millimeters, x, y, z,
                    nozzleIndex == 0 ? c : c2).add(hm.getHeadOffsets());
        }
        else {
        	result =  
            new Location(LengthUnit.Millimeters, x, y, zA, c)
                    .add(hm.getHeadOffsets());
        }
        return result.derive(null,null,null,normalizeAngle(result.getRotation()));    
    }

    @Override
    public void moveTo(ReferenceHeadMountable hm, Location location, double speed)
            throws Exception {
    	flushReceive();
        location = location.subtract(hm.getHeadOffsets());
        

        location = location.convertToUnits(LengthUnit.Millimeters);

        double x = location.getX();
        double y = location.getY();
        double z = location.getZ();
        double c = location.getRotation();
        
        // Bloquer à la limite basse de la machine
        x = Math.max(limitMachine.getX(),x);
        y = Math.max(limitMachine.getY(),y);
        z = Math.min(0,z);
        
        

        ReferenceNozzle nozzle = null;
        if (hm instanceof ReferenceNozzle) {
            nozzle = (ReferenceNozzle) hm;
        }

        /*
         * Only move Z if it's a Nozzle.
         */
        if (nozzle == null) {
            z = Double.NaN;
        }
        boolean flagXYmoved = false;

        StringBuffer sb = new StringBuffer();
        if (!Double.isNaN(x) && x != this.x) {
            sb.append(String.format(Locale.US, "X%2.2f ", x));
            this.x = x;
            flagXYmoved = true;
        }
        if (!Double.isNaN(y) && y != this.y) {
            sb.append(String.format(Locale.US, "Y%2.2f ", y));
            this.y = y;
            flagXYmoved = true;
        }
        int nozzleIndex = getNozzleIndex(nozzle);
        double oldC = (nozzleIndex == 0 ? this.c : this.c2);
        if (!Double.isNaN(c) && c != oldC) {
        	
            
            
            // We perform E moves solo because Smoothie doesn't like to make large E moves
            // with small X/Y moves, so we can't trust it to end up where we want it if we
            // do both at the same time.
            if (nozzleIndex == 0)
            		{
//            		sb.append(String.format(Locale.US, "A%2.2f ", c));

            		waitMachineReady(sendCommand(
            				String.format(Locale.US, "G1 A%2.2f F%2.0f", c, feedRateMmPerMinuteRotation * speed)),10000);
            		}
            else
            		{
//          		sb.append(String.format(Locale.US, "B%2.2f ", c));
          	waitMachineReady(sendCommand(
        				String.format(Locale.US, "G1 B%2.2f F%2.0f", c, feedRateMmPerMinuteRotation * speed)),10000);
            	
            		}
            
            if (nozzleIndex == 0) {
                this.c = c;
            }
            else {
                this.c2 = c;
            }
        }
        // Gestion de la bascule 
        if (!Double.isNaN(z)) {
        	double actual_a = Math.toDegrees(Math.asin((this.zA - zCamWheelRadius - zGap) / zCamRadius));
            double a = Math.toDegrees(Math.asin((z - zCamWheelRadius - zGap) / zCamRadius));
            double a_1mm =Math.toDegrees(Math.asin((z+SAFE_QUICK_Z - zCamWheelRadius - zGap) / zCamRadius));
            logger.debug("nozzle {} {} {}", z, zCamRadius, a);
                if (z != this.zA) {
                	double deep = a-this.zA;
                	if (deep<0)
                		{
                		// Optimisation, descente rapide, mais le dernier millimetre se fait doucement.
                		a_1mm = Math.min(actual_a, a_1mm);
                		boolean test = a<a_1mm;
                		if (getNozzleIndex(nozzle)==1)
            			{
            			a_1mm=-a_1mm;
            			a=-a;
            			}
                		
                		if (test)
                			{
                    		waitMachineReady(sendCommand(
                    				String.format(Locale.US, "G1 Z%2.2f F%2.0f", a_1mm, feedRateMmPerMinute * speed)),10000);
                			}
                		waitMachineReady(sendCommand(
            				String.format(Locale.US, "G1 Z%2.2f F%2.0f", a, feedRateMmPerMinuteZ * speed)),10000);
                		}
                	else
                		{
                		waitMachineReady(sendCommand(
                				// FCA_VENOM 
            				String.format(Locale.US, "G1 Z%2.2f F%2.0f", a, feedRateMmPerMinuteZ * speed)),10000);
                		}
                }
            
            this.zA = z;
        }

        if (sb.length() > 0) {

            sb.append(String.format(Locale.US, "F%2.0f", feedRateMmPerMinute * speed));
        	waitMachineReady(sendCommand(
    				String.format(Locale.US, "G1 " + sb.toString())),10000);

            
            
        }
    }

    protected double normalizeAngle(double angle) {
    	angle = (angle) %360.0 + 360;
    	return (angle) %360.0;
    }

    /**
     * Returns 0 or 1 for either the first or second Nozzle.
     * 
     * @param nozzle
     * @return
     */
    private int getNozzleIndex(Nozzle nozzle) {
        if (nozzle == null) {
            return 0;
        }
        return nozzle.getHead().getNozzles().indexOf(nozzle);
    }

    @Override
    public void pick(ReferenceNozzle nozzle) throws Exception {
        pump(true);
        if (getNozzleIndex(nozzle) == 0) {
            n1Exhaust(false);
            n1Vacuum(true);
            n1Picked = true;
            
        }
        else {
            n2Exhaust(false);
            n2Vacuum(true);
            n2Picked = true;
        }
        Thread.sleep(300);
    }

    @Override
    public void place(ReferenceNozzle nozzle) throws Exception {
        if (getNozzleIndex(nozzle) == 0) {
            n1Picked = false;
            if (!n1Picked && !n2Picked) {
                pump(false);
            }
            n1Vacuum(false);
            n1Exhaust(true);
            Thread.sleep(400);
            n1Exhaust(false);
        }
        else {
            n2Picked = false;
            if (!n1Picked && !n2Picked) {
                pump(false);
            }
            n2Vacuum(false);
            n2Exhaust(true);
            Thread.sleep(400);
            n2Exhaust(false);
        }
    Thread.sleep(200);
    }

    public synchronized void connect() throws Exception {
        super.connect();

        /**
         * Connection process notes:
         * 
         * On some platforms, as soon as we open the serial port it will reset the controller and
         * we'll start getting some data. On others, it may already be running and we will get
         * nothing on connect.
         */

        connected = false;
    	ledColor =  -1; 
        List<String> responses;
        readerThread = new Thread(this);
        readerThread.start();

        try {
            do {
                // Consume any buffered incoming data, including startup messages
                responses = sendCommand((String)null, 200);
            } while (!responses.isEmpty());
        }
        catch (Exception e) {
            // ignore timeouts
        }


        // Send a request to force Smoothie to respond and clear any buffers.
        // On my machine, at least, this causes Smoothie to re-send it's
        // startup message and I can't figure out why, but this works
        // around it.
        sendCommand(REINIT_CMD,0);
        responses = sendCommand(CONNECT_CMD, 2000);
        // Continue to read responses until we get the one that is the
        // result of the M114 command. When we see that we're connected.

        // We are connected to at least the minimum required version now
        // So perform some setup

        // Turn off the stepper drivers
        setEnabled(false);
        

        getCurrentPosition();
        connected = true;
    }

    protected void getCurrentPosition() throws Exception {
        List<String> responses;
        responses = sendCommand(GETPOS_CMD,1000);
        try
        	{
        	
        	List<String> partial = waitMachineReady(responses, 1000);
        	if (partial != null)
        		{
        		responses.addAll(partial);
        		}
        	}
        catch(Exception e)
        	{
        	}

        
        
        for (String response : responses) {
            if (response.contains("X position")) {
                String[] comps = response.split(":");
                String data = comps[1].trim();
                x = Double.parseDouble(data.split(" ")[0]);
                }
            if (response.contains("Y position")) {
                String[] comps = response.split(":");
                String data = comps[1].trim();
                y  = Double.parseDouble(data.split(" ")[0]);
                }
            if (response.contains("Z position")) {
                String[] comps = response.split(":");
                String data = comps[1].trim();
                zA = Double.parseDouble(data.split(" ")[0]);
                }
            if (response.contains("A position")) {
                String[] comps = response.split(":");
                String data = comps[1].trim();
                c =Double.parseDouble(data.split(" ")[0]);
                }
            if (response.contains("B position")) {
                String[] comps = response.split(":");
                String data = comps[1].trim();
                c2 =Double.parseDouble(data.split(" ")[0]);
                }
        }

        logger.debug("Current Position is {}, {}, {}, {}, {}", x, y, zA, c, c2);
    }

    public synchronized void disconnect() {
        disconnectRequested = true;
        connected = false;

        try {
            if (readerThread != null && readerThread.isAlive()) {
                readerThread.join();
            }
        }
        catch (Exception e) {
            logger.error("disconnect()", e);
        }

        try {
            super.disconnect();
        }
        catch (Exception e) {
            logger.error("disconnect()", e);
        }
        disconnectRequested = false;
    }
    
	protected List<String>   sendCommand(String[] commands, long timeout) throws Exception {
		List<String> responses = new ArrayList<>();
		for (String command : commands) {
			List<String> partial = sendCommand(command, timeout);
			if (partial != null)
				{
				responses.addAll(partial);
				}
		}
		return responses;
	}
	
	
	protected List<String>     waitMachineReady(List<String> preViousResponses,long timeout) throws Exception {
		List<String> responses = new ArrayList<>();
		if (preViousResponses != null)
			{
			for(String response:preViousResponses)
				{
				// lancement de la recherche de toutes les occurrences
				if (response.matches(patternMachineReady))
					return responses;
				}
			}
		if (timeout>=0)
			{
			List<String> partial = sendCommand((String)null,timeout,patternMachineReady);
			if (partial != null)
			{
				responses.addAll(partial);
			}
			}
		return responses;
	}

    

    protected List<String> sendCommand(String command) throws Exception {
        return sendCommand(command, 1000);
    }
    
	protected List<String> sendCommand(String command, long timeout) throws Exception {
		return sendCommand(command,timeout,patternResponseReceived);
		}
	
	protected void flushReceive()
	{
		while(responseQueue.poll() != null);		
	}


	protected List<String> sendCommand(String command, long timeout, String regularExpressionWaited) throws Exception {
		List<String> responses = new ArrayList<>();

		// Flush before send command
		

		// Send the command, if one was specified
		if (command != null) {
			logger.debug("sendCommand({}, {})", command, timeout);
			logger.debug(">> " + command);
			output.write(command.getBytes());
			output.write("\n".getBytes());
//			System.err.println(">" + command);
		}
		long timeInit = System.currentTimeMillis();
		String response = null;
		if (timeout == -1) {
			// Wait forever for a response to return from the reader.
			response = responseQueue.take();
		} else {
			// Wait up to timeout milliseconds for a response to return from
			// the reader.
			long timeEllapsed = 0;
			while (timeEllapsed <= timeout) {
				response = responseQueue.poll(timeEllapsed, TimeUnit.MILLISECONDS);
				// And if we got one, add it to the list of responses we'll
				// return.
				if (response != null) {
//					System.err.println("<"+response);
					responses.add(response);
					if (response.matches(regularExpressionWaited)) {
						return responses;
				}
				}
				timeEllapsed = System.currentTimeMillis() - timeInit;
			}
			if (responses.size() == 0 && timeout>0) {
				throw new Exception("Timeout waiting for response to " + command);
			}

			logger.debug("{} => {}", command, responses);
		}
	return responses;
	}

    public void run() {
        while (!disconnectRequested) {
            String line;
            try {
                line = readLine().trim();
            }
            catch (TimeoutException ex) {
                continue;
            }
            catch (IOException e) {
                logger.error("Read error", e);
                return;
            }
            line = line.trim();
            logger.debug("<< " + line);
            if (line.matches(".*err.*") )
				{
					System.err.println("<< " + line);
				}
            
            responseQueue.offer(line);
                synchronized (commandLock) {
                    commandLock.notify();
            }
        }
    }

    

    private List<String> drainResponseQueue() {
        List<String> responses = new ArrayList<>();
        String response;
        while ((response = responseQueue.poll()) != null) {
            responses.add(response);
        }
        return responses;
    }

    @Override
    public String getPropertySheetHolderTitle() {
        return getClass().getSimpleName();
    }

    @Override
    public PropertySheetHolder[] getChildPropertySheetHolders() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Action[] getPropertySheetHolderActions() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public PropertySheet[] getPropertySheets() {
        return new PropertySheet[] {new PropertySheetWizardAdapter(getConfigurationWizard())};
    }

    @Override
    public Wizard getConfigurationWizard() {
        return new Tm240TinygDriverWizard(this);
    }

    protected void n2Vacuum(boolean on) throws Exception {
        sendCommand(on ? "$out4=1" : "$out4=0");
    }

    protected void n2Exhaust(boolean on) throws Exception {
        sendCommand(on ? "$out5=1" : "$out5=0");
    }

    protected void n1Vacuum(boolean on) throws Exception {
        sendCommand(on ? "$out6=1" : "$out6=0");
    }

    protected void n1Exhaust(boolean on) throws Exception {
        sendCommand(on ? "$out7=1" : "$out7=0");
    }

    protected void pump(boolean on) throws Exception {
        sendCommand(on ? "$out8=1" : "$out8=0");
    }

    protected void led(boolean on) throws Exception {
        
    }
    
    protected boolean getStateOfInput(int nber_input) throws Exception
    {
    	String cmdReadEntry= new StringBuffer(String.format(Locale.US, "$in%d", nber_input)).toString();	
    	String cmdwaitAnswerEntry= new StringBuffer(String.format(Locale.US, "Input %d state\\:.*", nber_input)).toString();
    	List<String> answers =  sendCommand(cmdReadEntry,200,cmdwaitAnswerEntry);
    	for(String answer : answers)
		{
		if (answer.matches(cmdwaitAnswerEntry))
			{
			String[] tokens=answer.split("\\:");
			String data = tokens[1].trim();
			return data.matches("1") ? false : true; // !! inverted
			}
		}
	throw new Exception("Impossible to read State of input");
    	
    }
    
    
    protected boolean getStateOfNeedle() throws Exception 
    {
    return getStateOfInput(NBER_INPUT_NEEDLE);
    	
    }
    
    protected void setPositionXY(Location location) throws Exception
    {
    	String cmdSetLocation = new StringBuffer(String.format(Locale.US, "G28.3 X%2.2f Y%2.2f", location.getX(),location.getY())).toString();
    	sendCommand(cmdSetLocation);
    	
    }

    // JvN - We're going to do this with actuators, not a driver modification. 
//    @Override
//	public void setLightingColor(Color color) throws Exception {
//    	if (connected && color != null)
//    		{
//    		int levelColors = color.getRed() + 256* ( color.getGreen()+ color.getBlue()*256);
//    		if (levelColors != ledColor)
//    			{
//    			String cmdSetLeds= new StringBuffer(String.format(Locale.US, "$_leds=%d", levelColors)).toString();    	
//    			sendCommand(cmdSetLeds);
//    			ledColor = levelColors;
//    			}
//    			
//    		}
//        }


	
}
