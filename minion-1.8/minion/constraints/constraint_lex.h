/*
* Minion http://minion.sourceforge.net
* Copyright (C) 2006-09
*
* This program is free software; you can redistribute it and/or
* modify it under the terms of the GNU General Public License
* as published by the Free Software Foundation; either version 2
* of the License, or (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with this program; if not, write to the Free Software
* Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
*/

/** @help constraints;lexless Description
The constraint

   lexless(vec0, vec1)

takes two vectors vec0 and vec1 of the same length and ensures that
vec0 is lexicographically less than vec1 in any solution.
*/

/** @help constraints;lexless Notes
This constraint maintains GAC.
*/

/** @help constraints;lexless References
See also

   help constraints lexleq

for a similar constraint with non-strict lexicographic inequality.
*/

/** @help constraints;lexleq Description
The constraint

   lexleq(vec0, vec1)

takes two vectors vec0 and vec1 of the same length and ensures that
vec0 is lexicographically less than or equal to vec1 in any solution.
*/

/** @help constraints;lexleq Notes
This constraints achieves GAC.
*/

/** @help constraints;lexleq References
See also

   help constraints lexless

for a similar constraint with strict lexicographic inequality.
*/

#ifndef CONSTRAINT_LEX_H
#define CONSTRAINT_LEX_H

template<typename VarArray1, typename VarArray2, BOOL Less = false>
struct LexLeqConstraint : public AbstractConstraint
{
  virtual string constraint_name()
  { if(Less) return "lexless"; else return "lexleq"; }

  typedef LexLeqConstraint<VarArray2, VarArray1,!Less> NegConstraintType;
  typedef typename VarArray1::value_type ArrayVarRef1;
  typedef typename VarArray2::value_type ArrayVarRef2;

  ReversibleInt alpha;
  ReversibleInt beta;
  ReversibleInt F;

  VarArray1 x;
  VarArray2 y;

  CONSTRAINT_ARG_LIST2(x, y);

  LexLeqConstraint(StateObj* _stateObj,const VarArray1& _x, const VarArray2& _y) :
    AbstractConstraint(_stateObj), alpha(_stateObj), beta(_stateObj), F(_stateObj), x(_x), y(_y)
  { CHECK(x.size() == y.size(), "LexLeq and LexLess only work with equal length vectors"); }

  virtual triggerCollection setup_internal()
  {
    triggerCollection t;

    SysInt x_size = x.size();
    for(SysInt i=0; i < x_size; ++i)
    {
      t.push_back(make_trigger(x[i], Trigger(this, i), LowerBound));
      t.push_back(make_trigger(x[i], Trigger(this, i), UpperBound));
    }

    SysInt y_size = y.size();
    for(SysInt i=0; i < y_size; ++i)
    {
      t.push_back(make_trigger(y[i], Trigger(this, i), LowerBound));
      t.push_back(make_trigger(y[i], Trigger(this, i), UpperBound));
    }
    alpha = 0;
    if(Less)
      beta = x_size;
    else
      beta = 100000;
    F = 0;
    return t;
  }

  virtual AbstractConstraint* reverse_constraint()
  {
    return new LexLeqConstraint<VarArray2, VarArray1,!Less>(stateObj,y,x);
  }

  void updateAlpha(SysInt i) {
    SysInt n = x.size();
    if(Less)
    {
      if(i == n || i == beta)
      {
        getState(stateObj).setFailed(true);
        return;
      }
      if (!x[i].isAssigned() || !y[i].isAssigned() ||
          x[i].getAssignedValue() != y[i].getAssignedValue())  {
        alpha = i;
        propagate(i,DomainDelta::empty());
      }
      else updateAlpha(i+1);
    }
    else
    {
      while (i < n) {
        if (!x[i].isAssigned() || !y[i].isAssigned() ||
            x[i].getAssignedValue() != y[i].getAssignedValue())  {
          alpha = i ;
          propagate(i,DomainDelta::empty()) ;
          return ;
        }
        i++ ;
      }
      F = true ;
    }

  }

  ///////////////////////////////////////////////////////////////////////////////
  // updateBeta()
  void updateBeta(SysInt i) {
    SysInt a = alpha ;
    while (i >= a) {
      if (x[i].getMin() < y[i].getMax()) {
        beta = i+1 ;
        if (!(x[i].getMax() < y[i].getMin())) propagate(i,DomainDelta::empty()) ;
        return ;
      }
      i-- ;
    }
    getState(stateObj).setFailed(true);

  }

  virtual void propagate(DomainInt i_in, DomainDelta)
  {
    const SysInt i = checked_cast<SysInt>(i_in);
    PROP_INFO_ADDONE(Lex);
    if (F)
    {
      return ;
    }
    SysInt a = alpha, b = beta;

    //Not sure why we need this, but we seem to.
    if(b <= a)
    {
      getState(stateObj).setFailed(true);
      return;
    }

    if(Less)
    { if(i < a || i >=b) return; }
    else
    { if (i >= b) return ; }

    if (i == a && i+1 == b) {
      x[i].setMax(y[i].getMax()-1) ;
      y[i].setMin(x[i].getMin()+1) ;
      if (checkLex(i)) {
        F = true ;
        return ;
      }
    }
    else if (i == a && i+1 < b) {
      x[i].setMax(y[i].getMax()) ;
      y[i].setMin(x[i].getMin()) ;
      if (checkLex(i)) {
        F = true ;
        return ;
      }
      if (x[i].isAssigned() && y[i].isAssigned() && x[i].getAssignedValue() == y[i].getAssignedValue())
        updateAlpha(i+1) ;
    }
    else if (a < i && i < b) {
      if ((i == b-1 && x[i].getMin() == y[i].getMax()) || x[i].getMin() > y[i].getMax())
        updateBeta(i-1) ;
    }
  }


  BOOL checkLex(SysInt i) {
    if(Less)
    {
      return x[i].getMax() < y[i].getMin();
    }
    else
    {
      SysInt n = x.size() ;
      if (i == n-1) return (x[i].getMax() <= y[i].getMin()) ;
      else return (x[i].getMax() < y[i].getMin());
    }
  }

  virtual void full_propagate()
  {
    SysInt i, n = x.size() ;
    for (i = 0; i < n; i++) {
      if (!x[i].isAssigned()) break ;
      if (!y[i].isAssigned()) break ;
      if (x[i].getAssignedValue() != y[i].getAssignedValue()) break ;
    }
    if (i < n) {
      alpha = i ;
      if (checkLex(i)) {
        F = true ;
        return ;
      }
      SysInt betaBound = -1 ;
      for (; i < n; i++) {
        if (x[i].getMin() > y[i].getMax()) break ;
        if (x[i].getMin() == y[i].getMax()) {
          if (betaBound == -1) betaBound = i ;
        }
        else betaBound = -1 ;
      }
      if(!Less)
      {
        if (i == n) beta = 1000000 ;
        else if (betaBound == -1) beta = i ;
        else beta = betaBound ;
      }
      else
      {
        if(i == n) beta = n;
        if (betaBound == -1) beta = i ;
        else beta = betaBound ;
      }
      if (alpha >= beta) getState(stateObj).setFailed(true);
      propagate((SysInt)alpha,DomainDelta::empty()) ;             //initial propagation, if necessary.
    }
    else
    {
      if(Less)
        getState(stateObj).setFailed(true);
      else
        F = true;
    }
  }

  virtual BOOL check_assignment(DomainInt* v, SysInt v_size)
  {
    D_ASSERT(v_size == (SysInt)x.size() + (SysInt)y.size());
    size_t x_size = x.size();

    for(size_t i = 0;i < x_size; i++)
    {
      if(v[i] < v[i + x_size])
        return true;
      if(v[i] > v[i + x_size])
        return false;
    }
    if(Less)
      return false;
    else
      return true;
  }

  virtual bool get_satisfying_assignment(box<pair<SysInt,DomainInt> >& assignment)
  {
    size_t x_size = x.size();
    for(size_t i = 0; i < x_size; ++i)
    {
      DomainInt x_i_min = x[i].getMin();
      DomainInt y_i_max = y[i].getMax();

      if(x_i_min > y_i_max)
      {
        return false;
      }

      assignment.push_back(make_pair(i         , x_i_min));
      assignment.push_back(make_pair(i + x_size, y_i_max));
      if(x_i_min < y_i_max)
        return true;
    }

    if(Less)
      return false;
    return true;
  }

  virtual vector<AnyVarRef> get_vars()
  {
    vector<AnyVarRef> array_copy;
    for(UnsignedSysInt i=0;i<x.size();i++)
      array_copy.push_back(AnyVarRef(x[i]));

    for(UnsignedSysInt i=0;i<y.size();i++)
      array_copy.push_back(AnyVarRef(y[i]));
    return array_copy;
  }
};
#endif
