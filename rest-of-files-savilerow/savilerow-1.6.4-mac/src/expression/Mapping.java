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


import savilerow.model.SymbolTable;

import java.util.*;

//  This class represents a function from integers to integers. Used to deal
// with matrices indexed by e.g. int(1,3,5) by having a function 1->0, 3->1, 5->2, so return value of this function is 0,1,2
// Maps any unmapped values to a dummy value max+1, this avoids constraining the child var when this is flattened.

public class Mapping extends ASTNode
{
    public static final long serialVersionUID = 1L;
    public HashMap<Long, Long> map;
    public long defaultval;
    public Mapping(HashMap<Long, Long> m, ASTNode a)
    {
        super(a);
        map=m;
        
        // set defaultval.
        Long upper=null;
	    for(Map.Entry<Long, Long> it : map.entrySet()) {
	        long val=it.getValue();
	        if(upper == null || upper<val) upper=val;
	    }
	    defaultval=upper+1;
    }
    
    public String toString()
    {
        return "Mapping("+map.toString()+", "+getChild(0)+")";
    }
    
	public ASTNode copy()
	{
	    return new Mapping(map, getChild(0));
	}
	@Override
	public boolean equals(Object other)
	{
	    if(! (other instanceof Mapping))
	        return false;
	    return ((Mapping)other).map.equals(map) && getChild(0).equals(((Mapping)other).getChild(0));
	}
	
	@Override
    public int hashCode() {
        if(hashCache==Integer.MIN_VALUE) {
            int hash = getChild(0).hashCode()*17+map.hashCode();
            hashCache=hash;  // store
            return hash;
        }
        else {
            return hashCache;
        }
    }
	
	public boolean toFlatten(boolean propagate) { return true; }
	public boolean isNumerical() {
        return true;
    }
    
	public ASTNode simplify() {
	    if(getChild(0).isConstant()) {
	        if(map.containsKey(getChild(0).getValue())) {
	            return new NumberConstant(map.get(getChild(0).getValue()));
	        }
	        else {
	            return new NumberConstant(defaultval);
	        }
	    }
	    
	    return this;
	}
	public Intpair getBounds() {
	    Intpair a=getChild(0).getBounds();
	    long lower=defaultval;   ///  Assumes defaultval is always in.
	    long upper=defaultval;
	    
	    for(long i : map.keySet()) {
	        // Collect entries from map that are within range of the child. 
	        if(i<=a.upper && i>=a.lower) {
                long val=map.get(i);
                if(lower>val) lower=val;
                if(upper<val) upper=val;
            }
	    }
	    
	    return new Intpair(lower, upper);
	}
	
	public PairASTNode getBoundsAST() {
	    // Just look at the table for now.
	    Long lower=null;
	    Long upper=null;
	    
	    for(Map.Entry<Long, Long> a : map.entrySet()) {
	        long val=a.getValue();
	        if(lower == null || lower>val) lower=val;
	        if(upper == null || upper<val) upper=val;
	    }
	    if(lower == null || lower>defaultval) lower=defaultval;
	    if(upper == null || upper<defaultval) upper=defaultval;
	    
	    return new PairASTNode(new NumberConstant(lower), new NumberConstant(upper));  // add the default value.
	}
	
	/*public void toMinionWithAuxVar(StringBuffer b, ASTNode aux)
	{
	    /// Print a table constraint between the child and the aux var. 
	    b.append("lighttable([");
	    getChild(0).toMinion(b, false);
	    b.append(",");
	    aux.toMinion(b, false);
	    b.append("],{");
	    
	    Intpair a=getChild(0).getBounds();
	    
	    boolean first=true;
	    for(long val=a.lower; val<=a.upper; val++) {
	        if(!first) b.append(", ");
	        first=false;
	        
	        if(map.containsKey(val)) {
	            b.append("<"+val+","+map.get(val)+">");
	        }
	        else {
	            b.append("<"+val+","+defaultval+">");
	        }
	    }
	    
        b.append("})");
	}
	
	public void toFlatzincWithAuxVar(StringBuffer b, ASTNode aux)
	{
	    b.append("constraint table_int([");
	    getChild(0).toFlatzinc(b, false);
	    b.append(",");
	    aux.toFlatzinc(b, false);
	    b.append("],[");
	    
	    Intpair a=getChild(0).getBounds();
	    
	    for(long val=a.lower; val<=a.upper; val++) {
	        if(map.containsKey(val)) {
	            b.append(val+","+map.get(val)+",");
	        }
	        else {
	            b.append(val+","+defaultval+",");
	        }
	    }
	    
        b.append("]);");
	}*/
}
