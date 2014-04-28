package docsOracleandLearningJava4ed;

public class SuppressedExceptions {


	public static void main(String[] args) {
		try (DirtyResource d = new DirtyResource()){
			d.accessResource();
		} catch (Exception e) {
			System.err.println("Exception encountered: " + e.toString());
			final Throwable[] suppressedExceptions = e.getSuppressed();

			final int numSuppressed = suppressedExceptions.length;
			System.out.println(numSuppressed);
			if (numSuppressed > 0)
			{
				System.err.println("tThere are " + numSuppressed + " suppressed exceptions:");
			for (final Throwable exception : suppressedExceptions){
				System.err.println("tt" + exception.toString());
			}
			}
		} 
	}
	
	

}

 class DirtyResource implements AutoCloseable
{
	/**
	 * Need to call this method if you want to access this resource
	 * @throws RuntimeException no matter how you call this method
	 * */
	public void accessResource()
	{
		throw new RuntimeException("I wanted to access this resource. Bad luck. Its dirty resource !!!");
	}

	/**
	 * The overridden closure method from AutoCloseable interface
	 * @throws Exception which is thrown during closure of this dirty resource
	 * */
	@Override
	public void close() throws Exception
	{
		throw new NullPointerException("Remember me. I am your worst nightmare !! I am Null pointer exception !!");
	}
}
 
 
 //PRIOR TO JAVA 7
 /*
  package com.howtodoinjava.demo.core;

import static java.lang.System.err;

public class SuppressedExceptionDemoWithTryFinallyPrevious
{
	
    * Executable member function demonstrating suppressed exceptions
    * One exception is lost if not added in suppressed exceptions list
	public static void memberFunction() throws Exception
	{
		DirtyResource resource= new DirtyResource();
		try
	    {
	    	  resource.accessResource();
	    }
		finally
		{
			resource.close();
		}
	}

	public static void main(String[] arguments) throws Exception
   {
      try
      {
    	  memberFunction();
      }
      catch(Exception ex)
      {
    	  err.println("Exception encountered: " + ex.toString());
    	  final Throwable[] suppressedExceptions = ex.getSuppressed();
    	  final int numSuppressed = suppressedExceptions.length;
    	  if (numSuppressed > 0)
    	  {
    		  err.println("tThere are " + numSuppressed + " suppressed exceptions:");
	    	  for (final Throwable exception : suppressedExceptions)
	    	  {
	    		  err.println("tt" + exception.toString());
	    	  }
    	  }
      }
   }
}


   */
