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

// A set of integers. Can be infinite, subclassed by InfiniteDomain and FiniteDomain
// This class should never be constructed. It is here to be sub-classed.

// Simple indicates that it is not a matrix domain.

public abstract class SimpleDomain extends Domain
{
    public static final long serialVersionUID = 1L;
    
    public SimpleDomain() {
        super();
    }
    public SimpleDomain(ASTNode a) {
        super(a);
    }
    public SimpleDomain(ArrayList<ASTNode> ch) {
        super(ch);
    }
    
    @Override
    public boolean isSet() {
        return true;
    }
}
