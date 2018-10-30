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
import java.math.*;
import savilerow.model.*;

public class NumberConstant extends ASTNode {
    public static final long serialVersionUID = 1L;
    long num;
    public NumberConstant(long n) {
        super();
        num = n;
    }
    
    public ASTNode copy() {
        return new NumberConstant(num);
    }
    @Override
    public boolean equals(Object b) {
        if (! (b instanceof NumberConstant)) {
            return false;
        }
        // return num.equals(((NumberConstant)b).num);
        return num == ((NumberConstant) b).num;
    }
    
    @Override
    public int hashCode() {
        if(hashCache==Integer.MIN_VALUE) {
            int hash = (new Long(num)).hashCode();
            hashCache=hash;  // store
            return hash;
        }
        else {
            return hashCache;
        }
    }

    public String toString() {
        return String.valueOf(num);
    }

    public boolean isConstant() {
        return true;
    }
    public boolean isNumerical() {
        return true;
    }
    public boolean isRelation() {
        return false;
    }

    public long getValue() {
        return num;
    }
    public ArrayList<Intpair> getIntervalSet() {
        ArrayList<Intpair> intervals = new ArrayList<Intpair>();
        intervals.add(new Intpair(num, num));
        return intervals;
    }
    
    // Same methods as Identifier for sat encoding.
    public long directEncode(Sat satModel, long value) {
        return (value==num)?satModel.getTrue():(-satModel.getTrue());
    }
    public long orderEncode(Sat satModel, long value) {
        if(num<=value) {
            return satModel.getTrue();
        }
        else {
            return -satModel.getTrue();
        }
    }

    public void toMinion(StringBuffer b, boolean bool_context) {
        b.append(num);
    }
    public void toDominionParam(StringBuffer b) {
        b.append(num);
    }
    public void toFlatzinc(StringBuffer b, boolean bool_context) {
        assert !bool_context;
        b.append(num);
    }
    public void toMinizinc(StringBuffer b, boolean bool_context) {
        assert !bool_context;
        b.append(num);
    }
    public void toJSON(StringBuffer bf) {
        bf.append(num+"\n");
    }
}