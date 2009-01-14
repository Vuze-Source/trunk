/*
 * Created on Nov 9, 2007
 * Created by Paul Gardner
 * Copyright (C) 2007 Aelitis, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 * 
 * AELITIS, SAS au capital de 63.529,40 euros
 * 8 Allee Lenotre, La Grille Royale, 78600 Le Mesnil le Roi, France.
 *
 */


package org.gudy.azureus2.core3.util;

import java.util.LinkedList;

import com.aelitis.azureus.core.util.Java15Utils;


public abstract class 
AEThread2 
{
	public static final boolean TRACE_TIMES = false;
	
	private static final int MIN_RETAINED	= 2;
	private static final int MAX_RETAINED	= 16;
	
	private static final int THREAD_TIMEOUT_CHECK_PERIOD	= 10*1000;
	private static final int THREAD_TIMEOUT					= 60*1000;
	
	private static final LinkedList	daemon_threads = new LinkedList();
	
	private static final class JoinLock {
		volatile boolean released = false;
	}
	
	private static long	last_timeout_check;
	
	private static long	total_starts;
	private static long	total_creates;
	
	
	private threadWrapper	wrapper;
	
	private String				name;
	private boolean				daemon;
	private int					priority	= Thread.NORM_PRIORITY;
	private volatile JoinLock	lock		= new JoinLock();
	
	public
	AEThread2(
		String		_name,
		boolean		_daemon )
	{
		name		= _name;
		daemon		= _daemon;
	}
	
	/**
	 * multiple invocations of start() are possible, but discouraged if combined
	 * with other thread operations such as interrupt() or join()
	 */
	public void
	start()
	{
		JoinLock currentLock = lock;
		JoinLock newLock;
		
		synchronized (currentLock)
		{
			// create new lock in case this is a restart, all old .join()s will be locked on the old thread and thus released by the old thread
			if(currentLock.released)
				newLock = lock = new JoinLock();
			else
				newLock = currentLock;
		}
		
		if ( daemon ){
			
			synchronized( daemon_threads ){

				total_starts++;
				
				if ( daemon_threads.isEmpty()){
				
					total_creates++;
					
					wrapper = new threadWrapper( name, true );
					
				}else{
					
					wrapper = (threadWrapper)daemon_threads.removeLast();
					
					wrapper.setName( name );
				}
			}
		}else{
		
			wrapper = new threadWrapper( name, false );
		}
		
		if ( priority != wrapper.getPriority() ){
			
			wrapper.setPriority( priority );
		}
		
		wrapper.currentLock = newLock;
		
		wrapper.start( this, name );
	}
	
	public void
	setPriority(
		int		_priority )
	{
		priority	= _priority;
			
		if ( wrapper != null ){
			wrapper.setPriority( priority );
		}
	}
	
	public void
	setName(
		String	s )
	{
		name	= s;
		
		if ( wrapper != null ){
			
			wrapper.setName( name );
		}
	}
	
	public String
	getName()
	{
		return( name );
	}
	
	public void
	interrupt()
	{
		if ( wrapper == null ){
			
			throw new IllegalStateException( "Interrupted before started!" );
			
		}else{
			
			wrapper.interrupt();
		}
	}
	
	public boolean
	isCurrentThread()
	{
		return( wrapper == Thread.currentThread());
	}
	
	public String
	toString()
	{
		if ( wrapper == null ){
			
			return( name + " [daemon=" + daemon + ",priority=" + priority + "]" );
			
		}else{
				
			return( wrapper.toString());
		}
	}
	
	public abstract void
	run();
	
	public static boolean
	isOurThread(
		Thread	thread )
	{
		return( AEThread.isOurThread( thread ));
	}
	
	public static void
	setOurThread()
	{
		AEThread.setOurThread();
	}
	
	public static void
	setOurThread(
		Thread	thread )
	{
		AEThread.setOurThread( thread );
	}
	
	protected static class
	threadWrapper
		extends Thread
	{
		private AESemaphore sem;
		private AEThread2	target;
		private JoinLock	currentLock;
		
		private long		last_active_time;
		
		protected
		threadWrapper(
			String		name,
			boolean		daemon )
		{
			super( name );
			
			setDaemon( daemon );
		}
		
		public void
		run()
		{
			while( true ){
				
				synchronized( currentLock ){
					try{
						if ( TRACE_TIMES ){

							long 	start_time 	= SystemTime.getHighPrecisionCounter();
							long	start_cpu 	= Java15Utils.getThreadCPUTime();

							try{

								target.run();

							}finally{
								
								long	time_diff 	= ( SystemTime.getHighPrecisionCounter() - start_time )/1000000;
								long	cpu_diff	= ( Java15Utils.getThreadCPUTime() - start_cpu ) / 1000000;
								
								if ( cpu_diff > 10 || time_diff > 10 ){
								
									System.out.println( TimeFormatter.milliStamp() + ": Thread: " + target.getName() + ": " + cpu_diff + "/" + time_diff );
								}
							}
						}else{

							target.run();
						}
												
					}catch( Throwable e ){
						
						DebugLight.printStackTrace(e);
						
					}finally{
						
						target = null;

						currentLock.released = true;
						
						currentLock.notifyAll();						
					}
				}
								
				if ( isInterrupted() || !Thread.currentThread().isDaemon()){
					
					break;
					
				}else{
					
					synchronized( daemon_threads ){
						
						last_active_time	= SystemTime.getCurrentTime();
						
						if ( 	last_active_time < last_timeout_check ||
								last_active_time - last_timeout_check > THREAD_TIMEOUT_CHECK_PERIOD ){
							
							last_timeout_check	= last_active_time;
							
							while( daemon_threads.size() > 0 && daemon_threads.size() > MIN_RETAINED ){
								
								threadWrapper thread = (threadWrapper)daemon_threads.getFirst();
								
								long	thread_time = thread.last_active_time;
								
								if ( 	last_active_time < thread_time ||
										last_active_time - thread_time > THREAD_TIMEOUT ){
									
									daemon_threads.removeFirst();
									
									thread.retire();
									
								}else{
									
									break;
								}
							}
						}
						
						if ( daemon_threads.size() >= MAX_RETAINED ){
							
							return;
						}

						daemon_threads.addLast( this );

						setName( "AEThead2:parked[" + daemon_threads.size() + "]" );
						
						// System.out.println( "AEThread2: queue=" + daemon_threads.size() + ",creates=" + total_creates + ",starts=" + total_starts );
					}
					
					sem.reserve();
					
					if ( target == null ){
						
						break;
					}
				}
			}
		}
		
		protected void
		start(
			AEThread2	_target,
			String		_name )
		{
			target	= _target;
			
			setName( _name );
			
			if ( sem == null ){
				
				 sem = new AESemaphore( "AEThread2" );
				 
				 super.start();
				 
			}else{
				
				sem.release();
			}
		}
		
		protected void
		retire()
		{			
			sem.release();
		}
	}
	
	public void 
	join()
	{
		JoinLock currentLock = lock;

			// sync lock will be blocked by the thread
		
		synchronized( currentLock ){
			
				// wait in case the thread is not running yet
			
			while (!currentLock.released ){
				
				try{
					currentLock.wait();
					
				}catch( InterruptedException e ){ 
				}
			}
		}
	}
}
