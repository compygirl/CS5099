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

// Has children of type range (emptyrange) and parameter expressions
// Children are in order. 

public class IntegerDomain extends SimpleDomain
{
    public static final long serialVersionUID = 1L;
    public IntegerDomain(ArrayList<ASTNode> r)
    {
        if(r.size()==0) {
            r.add(new EmptyRange());
        }
        this.setChildren(r);
    }
    
    public IntegerDomain(ASTNode a) {
        super(a);
    }
    
    public ASTNode copy() {
	    return new IntegerDomain(getChildren());
	}
	
    public ArrayList<Long> getValueSet()
    {
        ArrayList<Long> i=new ArrayList<Long>();
	    for(int j=0; j<numChildren(); j++)
	    {
	        i.addAll(getChild(j).getValueSet());
	    }
	    Collections.sort(i);
	    
	    // copy without dups.  SHOULD NEVER HAVE DUPS!
	    ArrayList<Long> j=new ArrayList<Long>();
	    if(i.size()>0) j.add(i.get(0));
	    for(int k=1; k<i.size(); k++)
	    {
	        if(i.get(k-1)!=i.get(k))
	        {
	            j.add(i.get(k));
	        }
	    }
	    return j;
    }
    
    public Intpair getBounds() {
        Intpair a=getChild(0).getBounds();
        a.upper=getChild(numChildren()-1).getBounds().upper;
        return a;
    }
	public PairASTNode getBoundsAST() {
	    // Assumes the ranges are in ascending numerical order
	    return new PairASTNode(getChild(0).getBoundsAST().e1, getChild(numChildren()-1).getBoundsAST().e2);
	}
	
	public ArrayList<Intpair> getIntervalSet()
	{
	    ArrayList<Intpair> intervals=new ArrayList<Intpair>();
	    for(int i=0; i<numChildren(); i++) {
	        intervals.addAll(getChild(i).getIntervalSet());
	    }
	    return intervals;
	}
	
	@Override
	public ASTNode simplify() {
	    if(this.getCategory()==ASTNode.Constant) {
	        ArrayList<Intpair> a=this.getIntervalSet();
            
            // Now compare pairs to their neighbours and merge if overlapping.
            boolean changed=false;
            
            for(int i=0; i<a.size()-1; i++) {
                Intpair tmp=a.get(i).merge(a.get(i+1));
                if(tmp!=null) {
                    a.set(i, tmp);
                    a.remove(i+1);
                    i--;
                    changed=true;
                }
            }
            if(changed) return Intpair.makeDomain(a, false);
        }
        return this;
	}
	
	
	@Override
	public boolean isFiniteSetUpper() {
	    // Is it bounded above. 
	    ASTNode top=getChild(numChildren()-1);
	    if(top instanceof Range && top.getChild(1) instanceof PosInfinity) {
	        return false;
	    }
	    return true;
	}
	@Override
	public boolean isFiniteSetLower() {
	    // Is it bounded below.
	    ASTNode bottom=getChild(0);
	    if(bottom instanceof Range && bottom.getChild(0) instanceof NegInfinity) {
	        return false;
	    }
	    return true;
	}
	
	@Override
	public boolean isFiniteSet() {
	    return isFiniteSetUpper() && isFiniteSetLower();
	}
	
	public boolean toFlatten(boolean propagate) {return false;}
	
	public String toString() {
	    String st="int(";
	    for(int i=0; i<numChildren(); i++) {
	        st+=getChild(i).toString();
	        if(i<numChildren()-1) st+=",";
	    }
	    return st+")";
	}
	
	public void toDominionParam(StringBuffer b) {
	    for(int i=0; i<numChildren(); i++) {
	        getChild(i).toDominionParam(b);
	        if(i<numChildren()-1) b.append(",");
	    }
	}
	
	public ASTNode applyShift(int shift) {
	    for(int i=0; i<numChildren(); i++) {
	        setChild(i, getChild(i).applyShift(shift));
	    }
	    return this;
	}
	
	public boolean containsValue(long val) {
	    ASTNode vali=new NumberConstant(val);
	    for(int i=0; i<numChildren(); i++) {
	        if(getChild(i).equals(vali)) return true;
	        if(getChild(i) instanceof Range && getChild(i).containsValue(val)) return true;
	    }
	    return false;
	}
}
