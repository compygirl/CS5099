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
import savilerow.treetransformer.*;

import java.math.BigInteger;

// Obtain the bounds of an expression, for use when generating an aux variable.
// Long.MIN_VALUE and Long.MAX_VALUE are used to indicate an open range

public class Intpair implements Comparable<Intpair> {
    public long lower,upper;
    public Intpair(long l, long u) {lower=l; upper=u;}
    public String toString() {return "("+lower+","+upper+")";}
    
    public boolean isEmpty() {
        return lower>upper;
    }
    
    public Intpair intersect(Intpair other) {
        // Intersect this with the other, and make a new one. 
        long l=(lower<other.lower)?other.lower:lower;
        long u=(upper<other.upper)?upper:other.upper;
        return new Intpair(l, u);
    }
    
    // Union operation may not be exact. e.g. 1..2 and 10..20 gives 1..20.
    public Intpair union(Intpair other) {
        long l=(lower<other.lower)?lower:other.lower;
	    long u=(upper>other.upper)?upper:other.upper;
	    return new Intpair(l, u);
    }
    
    public Intpair merge(Intpair other) {
        if(upper < other.lower-1 || lower-1 > other.upper) {  //  3..5,6..10 should merge. 
            // can't be merged into one pair. 
            return null;
        }
        
        long l=(lower<other.lower)?lower:other.lower;
        long u=(upper<other.upper)?other.upper:upper;
        return new Intpair(l, u);
    }
    
    public ArrayList<Intpair> subtract(Intpair other) {
        // Do this / other and return a new Intpair if there is a change. 
        Intpair intersection=this.intersect(other);
        
        ArrayList<Intpair> ret=new ArrayList<Intpair>();
        
        // the piece lower than intersection
        long l1=lower;
        long u1=upper<(intersection.lower-1)?upper:(intersection.lower-1);
        
        // The piece above intersection
        long l2=lower>(intersection.upper+1)?lower:(intersection.upper+1);
        long u2=upper;
        
        Intpair new1=new Intpair(l1, u1);
        if(! new1.isEmpty()) ret.add(new1);
        
        Intpair new2=new Intpair(l2, u2);
        if(! new2.isEmpty()) ret.add(new2);
        
        return ret;
    }
    
    // Sort first by lowerbound then upperbound
    public int compareTo(Intpair other) {
        if(lower>other.lower) {
            return 1;
        }
        else if(lower==other.lower) {
            if(upper>other.upper) {
                return 1;
            }
            else if(upper==other.upper) {
                return 0;
            }
            else {
                return -1;
            }
        }
        else {
            // lower<o.lower
            return -1;
        }
    }
    
    // Convert BigInteger to long with saturation i.e. values larger than long max are mapped to long max. 
    static public long BigIntegerToLong(BigInteger b) {
        long ret;
        if(b.compareTo(BigInteger.valueOf(Long.MIN_VALUE)) <= 0) {
            ret=Long.MIN_VALUE;
        }
        else if(b.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) >= 0) {
            ret=Long.MAX_VALUE;
        }
        else {
            ret=b.longValue();
        }
        return ret;
    }
    
    // Convert an arraylist of Intpair to a domain. 
    // isBool indicates to make  a BooleanDomain
    public static ASTNode makeDomain(ArrayList<Intpair> a, boolean isBool) {
        
        if(isBool) {
            assert a.size()<=1;
            assert a.size()==0 || ( a.get(0).lower>=0 && a.get(0).upper<=1 );
            
            if(a.size()==0) {
                return new BooleanDomain(new EmptyRange());
            }
            
            return new BooleanDomain(new Range(new NumberConstant(a.get(0).lower), new NumberConstant(a.get(0).upper)));
        }
        
        // Need a special case for infinite domain open both ends.
        if(a.size()==1 && a.get(0).lower==Long.MIN_VALUE && a.get(0).upper==Long.MAX_VALUE) {
            return new IntegerDomain(new Range(null, null));
        }
        
        ArrayList<ASTNode> out2=new ArrayList<ASTNode>();
        
        for(int i=0; i<a.size(); i++) {
            Intpair pr=a.get(i);
            if(pr.lower==pr.upper) {
                out2.add(new NumberConstant(pr.lower));
            }
            else {
                ASTNode cl1;
                if(pr.lower==Long.MIN_VALUE) {
                    cl1=null;
                }
                else {
                    cl1=new NumberConstant(pr.lower);
                }
                
                ASTNode cl2;
                if(pr.upper==Long.MAX_VALUE) {
                    cl2=null;
                }
                else {
                    cl2=new NumberConstant(pr.upper);
                }
                
                out2.add(new Range(cl1, cl2));
            }
        }
        
        return new IntegerDomain(out2);
    }
    
    public static long numValues(ArrayList<Intpair> list) {
        long a=0;
        for(int i=0; i<list.size(); i++) {
            a=a+list.get(i).upper-list.get(i).lower+1;
        }
        return a;
    }
}

