package fr.lteconsulting;

import co.paralleluniverse.strands.channels.Channel;

class SpyCallMessage
{
	private final String methodName;

	private final Object[] parameters;

	private final Channel<Object> responseChannel;
	
	private final Object cookie;

	SpyCallMessage( String methodName, Object[] parameters, Channel<Object> responseChannel, Object cookie )
	{
		this.methodName = methodName;
		this.parameters = parameters;
		this.responseChannel = responseChannel;
		this.cookie = cookie;
	}
	
	public Object getCookie()
	{
		return cookie;
	}

	public String getMethodName()
	{
		return methodName;
	}

	public Object[] getParameters()
	{
		return parameters;
	}

	public Channel<Object> getResponseChannel()
	{
		return responseChannel;
	}

	@Override
	public String toString()
	{
		StringBuilder res = new StringBuilder();
		res.append( "[MESSAGE|" );
		res.append( methodName );
		res.append( "(" );
		if( parameters != null )
		{
			for( int i = 0; i < parameters.length; i++ )
			{
				if( i > 0 )
					res.append( ", " );
				res.append( "" + parameters[i] );
			}
		}
		res.append( ")" );
		if( responseChannel == null )
			res.append( " *no_resp_channel*" );
		res.append( "]" );
		return res.toString();
	}
}