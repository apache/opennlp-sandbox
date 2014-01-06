/* ==========================================
 * JGraphT : a free Java graph-theory library
 * ==========================================
 *
 * Project Info:  http://jgrapht.sourceforge.net/
 * Project Creator:  Barak Naveh (http://sourceforge.net/users/barak_naveh)
 *
 * (C) Copyright 2003-2008, by Barak Naveh and Contributors.
 *
 * This library is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2.1 of the License, or
 * (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this library; if not, write to the Free Software Foundation,
 * Inc.,
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307, USA.
 */
/* ----------------------
 * JGraphAdapterDemo.java
 * ----------------------
 * (C) Copyright 2003-2008, by Barak Naveh and Contributors.
 *
 * Original Author:  Barak Naveh
 * Contributor(s):   -
 *
 * $Id: JGraphAdapterDemo.java 725 2010-11-26 01:24:28Z perfecthash $
 *
 * Changes
 * -------
 * 03-Aug-2003 : Initial revision (BN);
 * 07-Nov-2003 : Adaptation to JGraph 3.0 (BN);
 *
 */
package opennlp.tools.parse_thicket.parse_thicket2graph;

import java.awt.*;
import java.awt.geom.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.swing.*;


import org.jgraph.*;
import org.jgraph.graph.*;

import org.jgrapht.*;
import org.jgrapht.ext.*;
import org.jgrapht.graph.*;


import org.jgrapht.graph.DefaultEdge;

public class ParseTreeVisualizer
extends JApplet
{
	//~ Static fields/initializers ---------------------------------------------

	private static final long serialVersionUID = 3256346823498765434L;
	private static final Color DEFAULT_BG_COLOR = Color.decode("#FAFBFF");
	private static final Dimension DEFAULT_SIZE = new Dimension(1200, 800);

	//~ Instance fields --------------------------------------------------------

	//
	private JGraphModelAdapter<String, DefaultEdge> jgAdapter;

	public void  showGraph(Graph g){
		ParseTreeVisualizer applet = new ParseTreeVisualizer();
		applet.importGraph(g);

		JFrame frame = new JFrame();
		frame.getContentPane().add(applet);
		frame.setTitle("Showing parse thicket");
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.pack();
		frame.setVisible(true);
	}

	// TODO cast to ParseGraphNode
	private void importGraph(Graph g) {
		// create a visualization using JGraph, via an adapter
		jgAdapter = new JGraphModelAdapter<String, DefaultEdge>(g);

		JGraph jgraph = new JGraph(jgAdapter);

		adjustDisplaySettings(jgraph);
		getContentPane().add(jgraph);
		resize(DEFAULT_SIZE);

		Set<String> vertexSet = ( Set<String>)g.vertexSet();
		int count=0;
		Map<Integer, Integer> level_count = new HashMap<Integer, Integer> ();

		for(String vertexStr: vertexSet){
			Integer key = 0;
			try {
				if (vertexStr.indexOf('#')>-1)
					key = Integer.parseInt(vertexStr.split("#")[1]);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			Integer howManyAlready = 0;

			if (key>0){
				 howManyAlready = level_count.get(key);
				if (howManyAlready==null){
					howManyAlready=0;
					level_count.put(key, 1);
				} else {
					level_count.put(key, howManyAlready+1);
				}
			}
			positionVertexAt(vertexStr, count+howManyAlready*50, count);
			count+=20;
		}


	}

	/**
	 * An alternative starting point for this demo, to also allow running this
	 * applet as an application.
	 *
	 * @param args ignored.
	 */
	public static void main(String [] args)
	{
		ParseTreeVisualizer applet = new ParseTreeVisualizer();
		applet.init();

		JFrame frame = new JFrame();
		frame.getContentPane().add(applet);
		frame.setTitle("JGraphT Adapter to JGraph Demo");
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.pack();
		frame.setVisible(true);
	}



	private void adjustDisplaySettings(JGraph jg)
	{
		jg.setPreferredSize(DEFAULT_SIZE);

		Color c = DEFAULT_BG_COLOR;
		String colorStr = null;

		try {
			colorStr = getParameter("bgcolor");
		} catch (Exception e) {
		}

		if (colorStr != null) {
			c = Color.decode(colorStr);
		}

		jg.setBackground(c);
	}

	@SuppressWarnings("unchecked") // FIXME hb 28-nov-05: See FIXME below
	private void positionVertexAt(Object vertex, int x, int y)
	{
		DefaultGraphCell cell = jgAdapter.getVertexCell(vertex);
		AttributeMap attr = cell.getAttributes();
		Rectangle2D bounds = GraphConstants.getBounds(attr);

		Rectangle2D newBounds =
				new Rectangle2D.Double(
						x,
						y,
						bounds.getWidth(),
						bounds.getHeight());

		GraphConstants.setBounds(attr, newBounds);

		// TODO: Clean up generics once JGraph goes generic
		AttributeMap cellAttr = new AttributeMap();
		cellAttr.put(cell, attr);
		jgAdapter.edit(cellAttr, null, null, null);
	}

}

// End JGraphAdapterDemo.java
