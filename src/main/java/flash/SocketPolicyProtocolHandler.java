package flash;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.logging.Logger;

import org.apache.coyote.Adapter;
import org.apache.coyote.ProtocolHandler;

/**
 * ProtocolHandler that serves a Flash Socket policy file. This is needed for 
 * Flash/Flex applications, that want to use Sockets to talk to the server.
 * You can configure the port (default is 843) and the path of the policy file 
 * ( default allows access from all domain to any port for http over sockets )
 */
public class SocketPolicyProtocolHandler implements ProtocolHandler {
    private final Logger logger = Logger.getLogger(SocketPolicyProtocolHandler.class.getName());
    
    /**
     * Port number to accept the connections. By default it uses port number 843.
     */
    private int port = 843;

    protected static final String EXPECTED_REQUEST = "<policy-file-request/>\0";
    protected static String POLICY_RESPONCE = "<cross-domain-policy><site-control permitted-cross-domain-policies=\"master-only\"/><allow-access-from domain=\"*\" to-ports=\"*\" /></cross-domain-policy>\0";

    private String policyFilePath;
    private Adapter adapter;
    private boolean isShutDownRequested = false;
    
    AsynchronousServerSocketChannel serverSocketChannel;
    private void startSocket() {
    	try {
			this.serverSocketChannel = AsynchronousServerSocketChannel.open();
			this.serverSocketChannel.bind( new InetSocketAddress( this.port ) );
			this.serverSocketChannel.accept( this.serverSocketChannel, new CompletionHandler<AsynchronousSocketChannel, AsynchronousServerSocketChannel>() {

				@Override
				public void completed(AsynchronousSocketChannel result, AsynchronousServerSocketChannel attachment) {
					logger.fine( "New connection accpted" );
					if( !shutDownRequested() ) {
						serverSocketChannel.accept( serverSocketChannel, this );
					}
					sendPolicy( result );
				}

				@Override
				public void failed(Throwable exc, AsynchronousServerSocketChannel attachment) {
					if( !shutDownRequested() ) {
						serverSocketChannel.accept( serverSocketChannel, this );
						logger.severe("Unable to create connection with client.\n" + exc.getMessage());
					}
				}
				
			});
		} catch (IOException e) {
			logger.severe("Unable to create server socket.\n" + e.getMessage());
		}
    }
    
    public void sendPolicy( AsynchronousSocketChannel clientSocket ) {
    	if( clientSocket != null ) {
	        try {
	        	
	        	ByteBuffer buffer = ByteBuffer.allocate( 1024 );
	        	Future<Integer> future = clientSocket.read( buffer );
				if( ( future.get() == 23 ) && ( new String( buffer.array() ) ).startsWith( EXPECTED_REQUEST ) ) {
					buffer = ByteBuffer.wrap( POLICY_RESPONCE.getBytes() );
					clientSocket.write( buffer );
				}
	        } catch (InterruptedException e) {
	        	logger.severe("Reading policy file request is interupted.\n" + e.getMessage());
			} catch (ExecutionException e) {
				logger.severe("Reading policy file request is interupted.\n" + e.getMessage());
	        } finally {
	            try {
	                clientSocket.close();
	                logger.fine("Closing client socket.");
	            } catch (IOException e) {
	            	logger.severe("Error while closing socket connection.\n" + e.getMessage());
	            }
	        }
    	}
    }

    private synchronized boolean shutDownRequested() {
        return this.isShutDownRequested;
    }
    
    private synchronized void requestShutDown() {
        this.isShutDownRequested = true;
        if( this.serverSocketChannel != null ) {
	        try {
	            this.serverSocketChannel.close();
	        } catch (IOException e) {
	        	logger.severe("Error closing server" + e.getMessage());
	        }
        }
    }

    public void setPolicyFile(String policyFile) {
        this.policyFilePath = policyFile;
    }

    public String getPolicyFile() {
        return this.policyFilePath;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public int getPort() {
        return this.port;
    }

	@Override
    public void setAdapter(Adapter adapter) {
        this.adapter = adapter;
    }

    @Override
    public Adapter getAdapter() {
        return adapter;
    }

    @Override
    public void init() {
    	
    	logger.info( "Initializing Flash Socket Policy protocol handler on port: " + port );
        if ( policyFilePath != null ) {
        	
        	logger.info( "Using policy file: " + policyFilePath );
            File file = new File( policyFilePath );
            try{
	            BufferedReader reader = new BufferedReader( new FileReader( file ) );
	            String line;
	            StringBuffer buffy = new StringBuffer();
	            while ( ( line = reader.readLine() ) != null ) {
	                buffy.append( line );
	            }
	            buffy.append("\0");
	            POLICY_RESPONCE = buffy.toString();
	            reader.close();
            } catch( IOException e ) {
            	logger.severe( "Unable to read policyfile from \"" + policyFilePath + "\".\n" + e.getStackTrace() );
            }
        } else {
        	logger.info( "Using default policy file: " + POLICY_RESPONCE );
        }
    }

    @Override
    public void start() throws Exception {
        startSocket();
    }

    @Override
    public void pause() throws Exception {
    }

    @Override
    public void resume() throws Exception {
    }

    @Override
    public void destroy() throws Exception {
    	this.requestShutDown();
    }

	@Override
	public Executor getExecutor() {
		return null;
	}

	@Override
	public boolean isAprRequired() {
		return false;
	}

	@Override
	public boolean isCometSupported() {
		return false;
	}

	@Override
	public boolean isCometTimeoutSupported() {
		return false;
	}

	@Override
	public boolean isSendfileSupported() {
		return false;
	}

	@Override
	public void stop() throws Exception {
		this.requestShutDown();
	}

}