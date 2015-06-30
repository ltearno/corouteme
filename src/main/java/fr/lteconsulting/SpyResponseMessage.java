package fr.lteconsulting;

public class SpyResponseMessage
{
	private final Object response;
	private final Object cookie;

	public SpyResponseMessage( Object object, Object cookie )
	{
		this.response = object;
		this.cookie = cookie;
	}

	public Object getObject()
	{
		return response;
	}
	
	public Object getCookie()
	{
		return cookie;
	}
}
