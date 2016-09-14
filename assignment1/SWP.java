/*===============================================================*
 *  File: SWP.java                                               *
 *                                                               *
 *  This class implements the sliding window protocol            *
 *  Used by VMach class					         *
 *  Uses the following classes: SWE, Packet, PFrame, PEvent,     *
 *                                                               *
 *  Author: Professor SUN Chengzheng                             *
 *          School of Computer Engineering                       *
 *          Nanyang Technological University                     *
 *          Singapore 639798                                     *
 *===============================================================*/

import java.util.Timer;
import java.util.TimerTask;

public class SWP {

/*========================================================================*
 the following are provided, do not change them!!
 *========================================================================*/
    //the following are protocol constants.
    public static final int MAX_SEQ = 7; 
    public static final int NR_BUFS = (MAX_SEQ + 1)/2;

    // the following are protocol variables
    private int oldest_frame = 0;
    private PEvent event = new PEvent();  
    private Packet out_buf[] = new Packet[NR_BUFS];

    //the following are used for simulation purpose only
    private SWE swe = null;
    private String sid = null;

    //Constructor
    public SWP(SWE sw, String s){
      swe = sw;
      sid = s;
    }

    //the following methods are all protocol related
    private void init(){
      for (int i = 0; i < NR_BUFS; i++){
	      out_buf[i] = new Packet();
      }
    }

    private void wait_for_event(PEvent e){
      swe.wait_for_event(e); //may be blocked
      oldest_frame = e.seq;  //set timeout frame seq
    }

    private void enable_network_layer(int nr_of_bufs) {
   //network layer is permitted to send if credit is available
	   swe.grant_credit(nr_of_bufs);
    }

    private void from_network_layer(Packet p) {
      swe.from_network_layer(p);
    }

    private void to_network_layer(Packet packet) {
	   swe.to_network_layer(packet);
    }

    private void to_physical_layer(PFrame fm)  {
      System.out.println("SWP: Sending frame: seq = " + fm.seq + 
			    " ack = " + fm.ack + " kind = " + 
			    PFrame.KIND[fm.kind] + " info = " + fm.info.data );
      System.out.flush();
      swe.to_physical_layer(fm);
    }

    private void from_physical_layer(PFrame fm) {
      PFrame fm1 = swe.from_physical_layer(); 
	    fm.kind = fm1.kind;
	    fm.seq = fm1.seq; 
	    fm.ack = fm1.ack;
	    fm.info = fm1.info;
    }


/*===========================================================================*
 	implement your Protocol Variables and Methods below: 
 *==========================================================================*/

  /*
  * By Chen Chin Jieh
  */

    private boolean no_nak = true;
    private Timer retransmit_timers[] = new Timer[NR_BUFS];
  	private Timer ack_timer = new Timer();
  	private final int RETRANSMIT_TIMEOUT = 300;
  	private final int ACK_TIMEOUT = 150;
 
    /* Runs the sliding window protocol */
    public void protocol6() {
      init();

      // Declare variables
      int ack_expected = 0;
      int next_frame_to_send = 0;
      int frame_expected = 0;
      int too_far = NR_BUFS;
      PFrame frame_received = new PFrame();
      Packet in_buf[] = new Packet [NR_BUFS];
      boolean arrived[] = new boolean [NR_BUFS];


      // Instantiate retransmission timers
      for (int i=0; i < NR_BUFS; i++) {
        arrived[i] = false;
        retransmit_timers[i] = new Timer();
      }

      enable_network_layer(NR_BUFS);

	    while(true) {	
	      wait_for_event(event);
		    switch(event.type) {

		      case (PEvent.NETWORK_LAYER_READY):
	          // Fetch new packet to send
	          from_network_layer(out_buf[next_frame_to_send % NR_BUFS]);

	          // Send Frame
	          send_frame(PFrame.DATA, next_frame_to_send, frame_expected, out_buf);

	          next_frame_to_send = inc(next_frame_to_send);
	          break; 

		      case (PEvent.FRAME_ARRIVAL):
	          from_physical_layer(frame_received); // Get the frame
	          if (frame_received.kind == PFrame.DATA) {
	            // Undamaged frame has arrived
	            if ((frame_received.seq != frame_expected) && no_nak) {
	            	// Frame out of order, and no NAK has been sent
	              send_frame(PFrame.NAK, 0,frame_expected, out_buf);
	            }
	            else {
	              start_ack_timer();
	            }

	            if (between(frame_expected, frame_received.seq, too_far) &&
	            (arrived[frame_received.seq % NR_BUFS]) == false) {
	              // Frame received within expected window
	              arrived[frame_received.seq % NR_BUFS] = true;
	              in_buf[frame_received.seq % NR_BUFS] = frame_received.info;

	              while (arrived[frame_expected % NR_BUFS]) {
	              	// Pass frames and advance window
	                to_network_layer(in_buf[frame_expected % NR_BUFS]);
	                no_nak = true;
	                arrived[frame_expected % NR_BUFS] = false;
	                frame_expected = inc(frame_expected);
	                too_far = inc(too_far);
	                start_ack_timer();
	              }
	            }
	          }

	          if (frame_received.kind == PFrame.NAK) {
	            // NAK received
	            if (between(ack_expected, (frame_received.ack+1) % (MAX_SEQ+1), next_frame_to_send))
	              send_frame(PFrame.DATA, (frame_received.ack+1) % (MAX_SEQ+1), frame_expected, out_buf);
	          }

	          while (between(ack_expected, frame_received.ack, next_frame_to_send)) {
	        		// Advance lower bound for expected ACKs to match received ACK
	            stop_timer(ack_expected % NR_BUFS);
	            ack_expected = inc(ack_expected);
	            enable_network_layer(1); // Free up one buffer slot
	          }

	          break;

          case (PEvent.CKSUM_ERR):
	          if (no_nak)
	            send_frame(PFrame.NAK, 0, frame_expected, out_buf);
	          break;

          case (PEvent.TIMEOUT): 
	          send_frame(PFrame.DATA, oldest_frame, frame_expected, out_buf);
	          break;

          case (PEvent.ACK_TIMEOUT): 
	          send_frame(PFrame.ACK, 0, frame_expected, out_buf);
	          break;

	        default: 
	        	System.out.println("SWP: undefined event type = " + event.type); 
	        	System.out.flush();
	          break;
	    }
	  }
	}

  /* Checks if number lies within a & b in circular fashion */
  private boolean between(int a, int number, int b) {
    return (((a <= number) && (number < b)) || ((b < a) && (a <= number)) || ((number < b) && (b < a)));
  }

  /* Increments a number, resetting from 0 if MAX_SEQ is exceeded */
  private int inc(int number) {
    int new_number = (number + 1) % (MAX_SEQ+1);
    return new_number;
  }

  /* Sends a frame to the physical layer */
  private void send_frame(int frame_kind, int frame_nr, int frame_expected, Packet[] packet_buffer) {
    PFrame frame = new PFrame();
  
    frame.kind = frame_kind;
    if (frame_kind == PFrame.DATA) {
      // Set frame info
      frame.info = packet_buffer[frame_nr % NR_BUFS];
    }

    frame.seq = frame_nr;
    frame.ack = (frame_expected + MAX_SEQ) % (MAX_SEQ + 1);

    if (frame_kind == PFrame.NAK) {
      no_nak = false;
    }

    // Send frame to physical layer
    to_physical_layer(frame);

    if (frame_kind == PFrame.DATA) {
      start_timer(frame_nr);
    }

    stop_ack_timer();
  }
 
  /* Starts the retransmission timer */
  private void start_timer(int seq) {
    stop_timer(seq);
    retransmit_timers[seq % NR_BUFS] = new Timer();
    retransmit_timers[seq % NR_BUFS].schedule(new RetransmitTask(seq), RETRANSMIT_TIMEOUT);
  }

  /* Stops the retransmission timer */
  private void stop_timer(int seq) {
    retransmit_timers[seq % NR_BUFS].cancel();
  }

  /* Starts the acknowledgement timer */
  private void start_ack_timer() {
    stop_ack_timer();
    ack_timer = new Timer();
    ack_timer.schedule(new AckTask(), ACK_TIMEOUT);
  }

  /* Stops the acknowledgement timer */
  private void stop_ack_timer() {
    ack_timer.cancel();
  }

  
  /*
  * Custom TimerTasks
  **/
  class RetransmitTask extends TimerTask {
    private int seq;

    public RetransmitTask(int seq) {
      this.seq = seq;
    }

    @Override
    public void run() {
      stop_timer(seq);
      swe.generate_timeout_event(seq);
    }
  }

  class AckTask extends TimerTask {

    @Override
    public void run() {
      stop_ack_timer();
      swe.generate_acktimeout_event();
    }
  }

}//End of class

/* Note: when start_timer() and stop_timer() are called, 
  the "seq" parameter must be the sequence number, rather 
  than the index of the timer array, 
  of the frame associated with this timer
*/

/* Note: In class SWE, the following two public methods are available:
   . generate_acktimeout_event() and
   . generate_timeout_event(seqnr).

   To call these two methods (for implementing timers),
   the "swe" object should be referred as follows:
     swe.generate_acktimeout_event(), or
     swe.generate_timeout_event(seqnr).
*/


