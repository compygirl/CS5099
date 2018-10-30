package savilerow.expression;
/*

    Savile Row http://savilerow.cs.st-andrews.ac.uk/
    Copyright (C) 2014, 2015 Peter Nightingale
    
    This file is part of Savile Row.
    
    Savile Row is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.
    
    Savile Row is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.
    
    You should have received a copy of the GNU General Public License
    along with Savile Row.  If not, see <http://www.gnu.org/licenses/>.

*/


import java.util.*;

// This one can now be an open range or a closed range. 

public class Range extends ASTNode
{
    public static final long serialVersionUID = 1L;
    
    public Range(ASTNode l, ASTNode r)
    {
        if(l==null) l=new NegInfinity();
        if(r==null) r=new PosInfinity();
        setChildren(l, r);
    }
    
	public ASTNode copy()
	{
	    return new Range(getChild(0), getChild(1));
	}
	
	@Override
	public boolean equals(Object other)
	{
	    if(!(other instanceof Range))
	        return false;
	    return getChild(0).equals(((ASTNode)other).getChild(0)) && getChild(1).equals(((ASTNode)other).getChild(1));
	}
	
	@Override
	public ASTNode simplify() {
	    if(getChild(0).isConstant() && getChild(1).isConstant() && getChild(0).getValue()>getChild(1).getValue()) {
	        System.out.println("WARNING: interval "+this+" is out of order. Rewriting to empty interval.");
	        return new EmptyRange();
	    }
	    if(getChild(0).equals(getChild(1))) {
	        return getChild(0);
	    }
	    return this;
	}
	
	public Intpair getBounds() {
	    return new Intpair(getChild(0).getBounds().lower, getChild(1).getBounds().upper);
	}
	
	// Calls getBoundsAST to get rid of any quantifier ID's 
	public PairASTNode getBoundsAST() {
	    return new PairASTNode(getChild(0).getBoundsAST().e1, getChild(1).getBoundsAST().e2);
	}
	
	public ArrayList<Long> getValueSet()
	{
	    // These methods should be phased out. If range is infinite, this will clearly fill up the memory. 
	    ArrayList<Long> list=new ArrayList<Long>();
	    long from=getChild(0).getValue();
	    long to=getChild(1).getValue();
	    for(long i=from; i<=to; i++) {
	        list.add(i);
	    }
	    return list;
	}
	
	public String toString() {
	    String st="";
	    if(! (getChild(0) instanceof NegInfinity)) st+=getChild(0).toString();
	    st+="..";
	    if(! (getChild(1) instanceof PosInfinity)) st+=getChild(1).toString();
	    return st;
	}
	
	public void toDominionParam(StringBuffer b) {
	    if(! (getChild(0) instanceof NegInfinity)) {
	        getChild(0).toDominionParam(b);
	    }
	    b.append("..");
	    if(! (getChild(1) instanceof PosInfinity)) {
	        getChild(1).toDominionParam(b);
	    }
	}
	public ASTNode applyShift(int shift) {
	    for(int i=0; i<numChildren(); i++) {
	        setChild(i, getChild(i).applyShift(shift));
	    }
	    return this;
	}
	public boolean containsValue(long val) {
	    return val>=getChild(0).getValue() && val<=getChild(1).getValue();
	}
	
	public boolean toFlatten(boolean propagate) {return false;}
	
	public ArrayList<Intpair> getIntervalSet()
	{
	    ArrayList<Intpair> i=new ArrayList<Intpair>();
	    // Uses Long.MIN_VALUE and Long.MAX_VALUE for unbounded numbers. 
	    i.add(new Intpair(getChild(0).getValue(), getChild(1).getValue()));
	    return i;
	}
}
