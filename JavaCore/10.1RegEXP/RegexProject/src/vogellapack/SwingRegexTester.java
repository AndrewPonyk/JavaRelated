package vogellapack;

/*
 * Copyright (c) Ian F. Darwin, http://www.darwinsys.com/, 1996-2002.
 * All rights reserved. Software written by Ian F. Darwin and others.
 * $Id: LICENSE,v 1.8 2004/02/09 03:33:38 ian Exp $
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR AND CONTRIBUTORS ``AS IS''
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE AUTHOR OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * 
 * Java, the Duke mascot, and all variants of Sun's Java "steaming coffee
 * cup" logo are trademarks of Sun Microsystems. Sun's, and James Gosling's,
 * pioneering role in inventing and promulgating (and standardizing) the Java 
 * language and environment is gratefully acknowledged.
 * 
 * The pioneering role of Dennis Ritchie and Bjarne Stroustrup, of AT&T, for
 * inventing predecessor languages C and C++ is also gratefully acknowledged.
 */

import java.awt.GridLayout;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * Standalone Swing GUI application for demonstrating REs. <br/>
 * TODO: Show the entire match, and $1 and up as captures that matched.
 * 
 * @author Ian Darwin, http://www.darwinsys.com/
 * @version #Id$
 */
public class SwingRegexTester extends JPanel {
	protected Pattern pattern;

	protected Matcher matcher;

	protected JTextField patternTF, stringTF;

	protected JCheckBox compiledOK;

	protected JRadioButton match, find, findAll;

	protected JTextField matchesTF;

	/** "main program" method - construct and show */
	public static void main(String[] av) {
		JFrame f = new JFrame("REDemo");
		f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		SwingRegexTester comp = new SwingRegexTester();
		f.setContentPane(comp);
		f.pack();
		f.setLocation(200, 200);
		f.setVisible(true);
	}

	/** Construct the REDemo object including its GUI */
	public SwingRegexTester() {
		super();

		JPanel top = new JPanel();
		top.add(new JLabel("Pattern:", JLabel.RIGHT));
		patternTF = new JTextField(20);
		patternTF.getDocument().addDocumentListener(new PatternListener());
		top.add(patternTF);
		top.add(new JLabel("Syntax OK?"));
		compiledOK = new JCheckBox();
		top.add(compiledOK);

		ChangeListener cl = new ChangeListener() {
			public void stateChanged(ChangeEvent ce) {
				tryMatch();
			}
		};
		JPanel switchPane = new JPanel();
		ButtonGroup bg = new ButtonGroup();
		match = new JRadioButton("Match");
		match.setSelected(true);
		match.addChangeListener(cl);
		bg.add(match);
		switchPane.add(match);
		find = new JRadioButton("Find");
		find.addChangeListener(cl);
		bg.add(find);
		switchPane.add(find);
		findAll = new JRadioButton("Find All");
		findAll.addChangeListener(cl);
		bg.add(findAll);
		switchPane.add(findAll);

		JPanel strPane = new JPanel();
		strPane.add(new JLabel("String:", JLabel.RIGHT));
		stringTF = new JTextField(20);
		stringTF.getDocument().addDocumentListener(new StringListener());
		strPane.add(stringTF);
		strPane.add(new JLabel("Matches:"));
		matchesTF = new JTextField(3);
		strPane.add(matchesTF);

		setLayout(new GridLayout(0, 1, 5, 5));
		add(top);
		add(strPane);
		add(switchPane);
	}

	protected void setMatches(boolean b) {
		if (b)
			matchesTF.setText("Yes");
		else
			matchesTF.setText("No");
	}

	protected void setMatches(int n) {
		matchesTF.setText(Integer.toString(n));
	}

	protected void tryCompile() {
		pattern = null;
		try {
			pattern = Pattern.compile(patternTF.getText());
			matcher = pattern.matcher("");
			compiledOK.setSelected(true);
		} catch (PatternSyntaxException ex) {
			compiledOK.setSelected(false);
		}
	}

	protected boolean tryMatch() {
		if (pattern == null)
			return false;
		matcher.reset(stringTF.getText());
		if (match.isSelected() && matcher.matches()) {
			setMatches(true);
			return true;
		}
		if (find.isSelected() && matcher.find()) {
			setMatches(true);
			return true;
		}
		if (findAll.isSelected()) {
			int i = 0;
			while (matcher.find()) {
				++i;
			}
			if (i > 0) {
				setMatches(i);
				return true;
			}
		}
		setMatches(false);
		return false;
	}

	/** Any change to the pattern tries to compile the result. */
	class PatternListener implements DocumentListener {

		public void changedUpdate(DocumentEvent ev) {
			tryCompile();
		}

		public void insertUpdate(DocumentEvent ev) {
			tryCompile();
		}

		public void removeUpdate(DocumentEvent ev) {
			tryCompile();
		}
	}

	/** Any change to the input string tries to match the result */
	class StringListener implements DocumentListener {

		public void changedUpdate(DocumentEvent ev) {
			tryMatch();
		}

		public void insertUpdate(DocumentEvent ev) {
			tryMatch();
		}

		public void removeUpdate(DocumentEvent ev) {
			tryMatch();
		}
	}
}
