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

/** @help constraints;sumleq Description
The constraint

   sumleq(vec, c)

ensures that sum(vec) <= c.
*/

/** @help constraints;sumgeq Description
The constraint

   sumgeq(vec, c)

ensures that sum(vec) >= c.
*/

// This is the standard implementation of sumleq (and sumgeq)

#ifndef CONSTRAINT_FULLSUM_H
#define CONSTRAINT_FULLSUM_H


#ifdef P
#undef P
#endif

//#define P(x) cout << x << endl
#define P(x)

/// V1 + ... Vn <= X
/// is_reversed checks if we are in the case where reverse_constraint was previously called.
template<typename VarArray, typename VarSum, BOOL is_reversed = false>
struct LessEqualSumConstraint : public AbstractConstraint
{
  virtual string constraint_name()
  { return "sumleq"; }

  CONSTRAINT_WEIGHTED_REVERSIBLE_ARG_LIST2("weighted", "sumleq", "sumgeq", var_array, var_sum);

  //typedef BoolLessSumConstraint<VarArray, VarSum,1-VarToCount> NegConstraintType;
  typedef typename VarArray::value_type VarRef;

  bool no_negatives;

  VarArray var_array;
  VarSum var_sum;
  DomainInt max_looseness;
  Reversible<DomainInt> var_array_min_sum;
  LessEqualSumConstraint(StateObj* _stateObj, const VarArray& _var_array, VarSum _var_sum) :
    AbstractConstraint(_stateObj), var_array(_var_array), var_sum(_var_sum),
    var_array_min_sum(_stateObj)
  {
      BigInt accumulator=0;
      for(SysInt i=0; i<(SysInt)var_array.size(); i++) {
          accumulator+= checked_cast<SysInt>(max( abs(var_array[i].getInitialMax()), abs(var_array[i].getInitialMin()) ));
          CHECKSIZE(accumulator, "Sum of bounds of variables too large in sum constraint");
      }
      accumulator+= checked_cast<SysInt>(max( abs(var_sum.getInitialMax()), abs(var_sum.getInitialMin()) ));
      CHECKSIZE(accumulator, "Sum of bounds of variables too large in sum constraint");

    no_negatives = true;
    for(SysInt i = 0; i < (SysInt)var_array.size(); ++i)
    {
      if(var_array[i].getInitialMin() < 0)
      {
        no_negatives = false;
        return;
      }
    }
  }

  virtual triggerCollection setup_internal()
  {
    triggerCollection t;

    SysInt array_size = var_array.size();
    for(SysInt i = 0; i < array_size; ++i)
    {
      t.push_back(make_trigger(var_array[i], Trigger(this, i), LowerBound));
    }
    t.push_back(make_trigger(var_sum, Trigger(this, -1), UpperBound));
    return t;
  }

  DomainInt get_real_min_sum()
  {
    DomainInt min_sum = 0;
    for(typename VarArray::iterator it = var_array.begin(); it != var_array.end(); ++it)
      min_sum += it->getMin();
    return min_sum;
  }

  virtual void propagate(DomainInt prop_val, DomainDelta domain_change)
  {
    P("Prop: " << prop_val);
    PROP_INFO_ADDONE(FullSum);
    DomainInt sum = var_array_min_sum;
    if(prop_val != -1)
    { // One of the array changed
      DomainInt change = var_array[checked_cast<SysInt>(prop_val)].getDomainChange(domain_change);
      P(" Change: " << change);
      D_ASSERT(change >= 0);
      sum += change;
      var_array_min_sum = sum;
    }

    var_sum.setMin(sum);
    if(getState(stateObj).isFailed())
        return;
    D_ASSERT(sum <= get_real_min_sum());

    DomainInt looseness = var_sum.getMax() - sum;
    if(looseness < 0)
    {
      getState(stateObj).setFailed(true);
      return;
    }

    if(looseness < max_looseness)
    {
      for(typename VarArray::iterator it = var_array.begin(); it != var_array.end(); ++it)
        it->setMax(it->getMin() + looseness);
    }
  }

  virtual void full_propagate()
  {
    P("Full Prop");
    DomainInt min_sum = 0;
    DomainInt max_diff = 0;
    for(typename VarArray::iterator it = var_array.begin(); it != var_array.end(); ++it)
    {
      min_sum += it->getMin();
      max_diff = max(max_diff, it->getMax() - it->getMin());
    }

    var_array_min_sum = min_sum;
    D_ASSERT(min_sum == get_real_min_sum());
    max_looseness = max_diff;
    if(!var_array.empty())
      propagate(0,DomainDelta::empty());
    else
      var_sum.setMin(0);
  }

  virtual BOOL check_assignment(DomainInt* v, SysInt v_size)
  {
    D_ASSERT(v_size == (SysInt)var_array.size() + 1);
    DomainInt sum = 0;
    for(SysInt i = 0; i < v_size - 1; i++)
      sum += v[i];
    return sum <= *(v + v_size - 1);
  }

  virtual bool get_satisfying_assignment(box<pair<SysInt,DomainInt> >& assignment)
  {
    P("GSA");
    DomainInt sum_value = 0;
    SysInt v_size = var_array.size();

    if(no_negatives)   // How are the two cases different? They look identical.
    {
      DomainInt max_sum = var_sum.getMax();
      assignment.push_back(make_pair(v_size, max_sum));
      for(SysInt i = 0; i < v_size && sum_value <= max_sum; ++i)
      {
        DomainInt min_val = var_array[i].getMin();
        assignment.push_back(make_pair(i, min_val));
        sum_value += min_val;
      }
      P("A" << (sum_value <= max_sum));
      return (sum_value <= max_sum);
    }
    else
    {
      for(SysInt i = 0; i < v_size; ++i)
      {
        assignment.push_back(make_pair(i, var_array[i].getMin()));
        sum_value += var_array[i].getMin();
      }
      P("B" << (sum_value <= var_sum.getMax()));
      if(sum_value > var_sum.getMax())
        return false;
      else
        assignment.push_back(make_pair(v_size, var_sum.getMax()));
      return true;
    }
  }


  virtual vector<AnyVarRef> get_vars()
  {
    vector<AnyVarRef> array_copy(var_array.size() + 1);
    for(UnsignedSysInt i = 0; i < var_array.size(); ++i)
      array_copy[i] = var_array[i];
    array_copy[var_array.size()] = var_sum;
    return array_copy;
  }

  virtual AbstractConstraint* reverse_constraint()
  { return rev_implement<is_reversed>(); }

 template<bool b>
  typename std::enable_if<!b, AbstractConstraint*>::type rev_implement()
  {
    typename NegType<VarArray>::type new_var_array(var_array.size());
    for(UnsignedSysInt i = 0; i < var_array.size(); ++i)
      new_var_array[i] = VarNegRef(var_array[i]);

    typedef typename ShiftType<typename NegType<VarSum>::type, compiletime_val<SysInt, -1> >::type SumType;
    SumType new_sum = ShiftVarRef( VarNegRef(var_sum), compiletime_val<SysInt, -1>());

    return new LessEqualSumConstraint<typename NegType<VarArray>::type, SumType, true>
      (stateObj, new_var_array, new_sum);
  }

  template<bool b>
  typename std::enable_if<b, AbstractConstraint*>::type rev_implement()
  {
    vector<AnyVarRef> new_var_array(var_array.size());
    for(UnsignedSysInt i = 0; i < var_array.size(); ++i)
    new_var_array[i] = VarNegRef(var_array[i]);

    typedef typename ShiftType<typename NegType<VarSum>::type, compiletime_val<SysInt, -1> >::type SumType;
    SumType new_sum = ShiftVarRef( VarNegRef(var_sum), compiletime_val<SysInt, -1>());

    return new LessEqualSumConstraint<vector<AnyVarRef>, AnyVarRef, true>
      (stateObj, new_var_array, new_sum);
       }

  };

#endif
