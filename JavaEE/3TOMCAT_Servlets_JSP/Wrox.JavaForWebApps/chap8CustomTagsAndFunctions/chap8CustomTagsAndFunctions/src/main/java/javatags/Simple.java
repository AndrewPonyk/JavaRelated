package javatags;

import java.io.IOException;

import javax.servlet.jsp.JspException;
import javax.servlet.jsp.PageContext;
import javax.servlet.jsp.tagext.SimpleTagSupport;

public class Simple extends SimpleTagSupport{

	private int repeat;

	@Override
	public void doTag() throws JspException, IOException {
		PageContext ctx =  (PageContext) getJspContext();
		ctx.getOut().write("<b>Hello I'm 'Simple' tags written in Java; this is paramter : " + repeat + "</b>");

		ctx.setAttribute("fromSimple", "Page value set in Simple.java tag");
	}

	public int getRepeat() {
		return repeat;
	}

	public void setRepeat(int repeat) {
		this.repeat = repeat;
	}
}