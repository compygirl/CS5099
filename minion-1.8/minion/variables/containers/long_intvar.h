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

/** @help variables;discrete Description
In discrete variables, the domain ranges between the specified lower and upper
bounds, but during search any domain value may be pruned, i.e., propagation and
search may punch arbitrary holes in the domain.
*/

/** @help variables;discrete Example
Declaration of a discrete variable x with domain {1,2,3,4} in input file:

DISCRETE x {1..4}

Use of this variable in a constraint:

eq(x, 2) #variable x equals 2
*/

#include "../../constraints/constraint_abstract.h"

template<typename d_type>
struct BigRangeVarContainer;

template<typename d_type>
struct BigRangeVarRef_internal_template
{
  static const BOOL isBool = false;
  static const BoundType isBoundConst = Bound_No;
  static string name() { return "LongRange"; }
  BOOL isBound() const
  { return false;}

  AnyVarRef popOneMapper() const
  { FATAL_REPORTABLE_ERROR(); }

  SysInt var_num;

#ifdef MANY_VAR_CONTAINERS
  BigRangeVarContainer<d_type>* rangeCon;
  BigRangeVarContainer<d_type>& getCon() const { return *rangeCon; }

  BigRangeVarRef_internal_template() : var_num(-1), rangeCon(NULL)
  {}

  explicit BigRangeVarRef_internal_template(BigRangeVarContainer<d_type>* con, DomainInt i) :
  var_num(checked_cast<SysInt>(i)), rangeCon(con)
  {}
#else
  static BigRangeVarContainer<d_type>& getCon_Static();
  BigRangeVarRef_internal_template() : var_num(-1)
  {}

  explicit BigRangeVarRef_internal_template(BigRangeVarContainer<d_type>*, DomainInt i) :
  var_num(checked_cast<SysInt>(i))
  {}
#endif

};

#ifdef MORE_SEARCH_INFO
typedef InfoRefType<VarRefType<BigRangeVarRef_internal_template<BitContainerType> >, VAR_INFO_BIGRANGEVAR> BigRangeVarRef;
#else
typedef VarRefType<BigRangeVarRef_internal_template<BitContainerType> > BigRangeVarRef;
#endif

template<typename d_type>
struct BigRangeVarContainer {
  typedef BigRangeVarRef_internal_template<BitContainerType> BigRangeVarRef_internal;

  StateObj* stateObj;

  BigRangeVarContainer(StateObj* _stateObj) : stateObj(_stateObj),
                                              bms_array(&getMemory(stateObj).monotonicSet()),
                                              trigger_list(stateObj, false),
                                              var_count_m(0), lock_m(0)
  {
    // Store where the first variable will go.
    var_offset.push_back(0);
  }

  typedef DomainInt domain_bound_type;
  void* bound_data;
  MonotonicSet *bms_array;
  TriggerList trigger_list;

  /// Initial bounds of each variable
  vector<pair<DomainInt,DomainInt> > initial_bounds;
  /// Position in the variable data (in counts of d_type) of where each variable starts
  vector<DomainInt> var_offset;
  /// Constraints variable participates in
  vector<vector<AbstractConstraint*> > constraints;
#ifdef WDEG
  vector<DomainInt> wdegs;
#endif


  UnsignedSysInt var_count_m;
  BOOL lock_m;

#ifdef SLOW_DOM_SIZE
#define BOUND_DATA_SIZE 2
#else
#define BOUND_DATA_SIZE 3
#endif

  domain_bound_type& lower_bound(BigRangeVarRef_internal i) const
  { return static_cast<domain_bound_type*>(bound_data)[i.var_num*BOUND_DATA_SIZE]; }

  domain_bound_type& upper_bound(BigRangeVarRef_internal i) const
  { return static_cast<domain_bound_type*>(bound_data)[i.var_num*BOUND_DATA_SIZE + 1]; }

#ifdef SLOW_DOM_SIZE
  void reduce_dom_size(BigRangeVarRef_internal i)
  {}
#else
  domain_bound_type& dom_size(BigRangeVarRef_internal i) const
  { return static_cast<domain_bound_type*>(bound_data)[i.var_num*BOUND_DATA_SIZE + 2]; }

  void reduce_dom_size(BigRangeVarRef_internal i)
  { dom_size(i)-=1; }
#endif

  /// Find new "true" upper bound.
  /// This should be used by first setting the value of upper_bound(d), then calling
  /// this function to move this value past any removed values.
  DomainInt find_new_upper_bound(BigRangeVarRef_internal d)
  {
    DomainInt lower = lower_bound(d);
    DomainInt old_up_bound = upper_bound(d);
    DomainInt loopvar = old_up_bound;
    //DomainInt low_bound = initial_bounds[d.var_num].first;
    if(loopvar < lower)
    {
      getState(stateObj).setFailed(true);
      /// Here just remove the value which should lead to the least work.
      return upper_bound(d);
    }
    if(bms_array->isMember(var_offset[d.var_num] + loopvar) && (loopvar >= lower))
      return upper_bound(d);
    --loopvar;
    for(; loopvar >= lower; --loopvar)
    {
      if(bms_array->isMember(var_offset[d.var_num] + loopvar))
        return loopvar;
    }
    getState(stateObj).setFailed(true);
    return old_up_bound;
  }

  /// Find new "true" lower bound.
  /// This should be used by first setting the value of lower_bound(d), then calling
  /// this function to move this value past any removed values.
  DomainInt find_new_lower_bound(BigRangeVarRef_internal d)
  {
    DomainInt upper = upper_bound(d);
    DomainInt old_low_bound = lower_bound(d);
    DomainInt loopvar = old_low_bound;
    //DomainInt low_bound = initial_bounds[d.var_num].first;
    if(loopvar > upper)
    {
      getState(stateObj).setFailed(true);
      /// Here just remove the value which should lead to the least work.
      return lower_bound(d);
    }
    if(bms_array->isMember(var_offset[d.var_num] + loopvar) && (loopvar <= upper))
      return lower_bound(d);
    ++loopvar;
    for(; loopvar <= upper; ++loopvar)
    {
      if(bms_array->isMember(var_offset[d.var_num] + loopvar))
        return loopvar;
    }
    getState(stateObj).setFailed(true);
    return old_low_bound;
  }

 void lock()
  {
    D_ASSERT(!lock_m);
    lock_m = true;
    //bms_array->lock(stateObj); // gets locked in constraint_setup.cpp
 }

  void addVariables(const vector<pair<SysInt, Bounds> >& new_domains)
  {
    D_ASSERT(!lock_m);
    for(SysInt i = 0; i < (SysInt)new_domains.size(); ++i)
    {
      for(DomainInt j = 0; j < new_domains[i].first; ++j)
      {
        initial_bounds.push_back(make_pair(new_domains[i].second.lower_bound, new_domains[i].second.upper_bound));
        DomainInt domain_size;
        domain_size = new_domains[i].second.upper_bound - new_domains[i].second.lower_bound + 1;
        var_offset.push_back( var_offset.back() + domain_size);
        var_count_m++;
      }
      constraints.resize(var_count_m);
#ifdef WDEG
       wdegs.resize(var_count_m);
#endif
    }

    bound_data = getMemory(stateObj).backTrack().request_bytes(var_count_m * BOUND_DATA_SIZE * sizeof(domain_bound_type));
    DomainInt temp1=bms_array->request_storage(var_offset.back());

    // correct var_offsets to start at the start of our block.
    if(temp1>0)
    {
        for(SysInt i=0;i<(SysInt)var_offset.size(); ++i) var_offset[i]+=temp1;
    }

    for(SysInt j = 0; j < (SysInt)var_count_m; ++j) {
           var_offset[j] = var_offset[j] - initial_bounds[j].first;
    };

    domain_bound_type* bound_ptr = static_cast<domain_bound_type*>(bound_data);

    DomainInt min_domain_val = 0;
    DomainInt max_domain_val = 0;
    if(!initial_bounds.empty())
    {
      min_domain_val = initial_bounds[0].first;
      max_domain_val = initial_bounds[0].second;
      for(UnsignedSysInt i = 0; i < var_count_m; ++i)
      {
        bound_ptr[BOUND_DATA_SIZE*i] = initial_bounds[i].first;
        bound_ptr[BOUND_DATA_SIZE*i+1] = initial_bounds[i].second;
#ifndef SLOW_DOM_SIZE
        bound_ptr[BOUND_DATA_SIZE*i+2] = initial_bounds[i].second - initial_bounds[i].first + 1;
#endif
        min_domain_val = mymin(initial_bounds[i].first, min_domain_val);
        max_domain_val = mymax(initial_bounds[i].second, max_domain_val);
      }
    }
    trigger_list.lock(var_count_m, min_domain_val, max_domain_val);
  }

  BOOL isAssigned(BigRangeVarRef_internal d) const
  {
    D_ASSERT(lock_m);
    return lower_bound(d) == upper_bound(d);
  }

  DomainInt getAssignedValue(BigRangeVarRef_internal d) const
  {
    D_ASSERT(lock_m);
    D_ASSERT(isAssigned(d));
    return getMin(d);
  }

  BOOL inDomain(BigRangeVarRef_internal d, DomainInt i) const
  {
    D_ASSERT(lock_m);
    if (i < lower_bound(d) || i > upper_bound(d))
      return false;
    return bms_array->isMember(var_offset[d.var_num] + i);
  }

  // Warning: If this is ever changed, be sure to check through the code for other places
  // where bms_array is used directly.
  BOOL inDomain_noBoundCheck(BigRangeVarRef_internal d, DomainInt i) const
  {
    D_ASSERT(lock_m);
    D_ASSERT(i >= lower_bound(d));
    D_ASSERT(i <= upper_bound(d));
    return bms_array->isMember(var_offset[d.var_num] + i );
  }

  DomainInt getDomSize_Check(BigRangeVarRef_internal d) const
  {
    DomainInt dom_size = 0;
    for(DomainInt i = this->getMin(d); i <= this->getMax(d); ++i)
    {
      if(this->inDomain(d,i))
        dom_size++;
    }
    return dom_size;
  }

  DomainInt getDomSize(BigRangeVarRef_internal d) const
  {
    D_ASSERT(getDomSize_Check(d) == dom_size(d));
    return dom_size(d);
  }

  DomainInt getMin(BigRangeVarRef_internal d) const
  {
    D_ASSERT(lock_m);
    D_ASSERT(getState(stateObj).isFailed() || ( inDomain(d, lower_bound(d)) && inDomain(d, upper_bound(d)) ) );
    return lower_bound(d);
  }

  DomainInt getMax(BigRangeVarRef_internal d) const
  {
    D_ASSERT(lock_m);
    D_ASSERT(getState(stateObj).isFailed() || ( inDomain(d, lower_bound(d)) && inDomain(d, upper_bound(d)) ) );
    return upper_bound(d);
  }

  DomainInt getInitialMin(BigRangeVarRef_internal d) const
  { return initial_bounds[d.var_num].first; }

  DomainInt getInitialMax(BigRangeVarRef_internal d) const
  { return initial_bounds[d.var_num].second; }

  void removeFromDomain(BigRangeVarRef_internal d, DomainInt i)
  {
#ifdef DEBUG
    cout << "Calling removeFromDomain: " << d.var_num << " " << i << " ["
         << lower_bound(d) << ":" << upper_bound(d) << "] original ["
         << getInitialMin(d) << ":" << getInitialMax(d) << "]"
         << endl;
    //bms_pointer(d)->print_state();
    bms_array->print_state();
#endif
    D_ASSERT(lock_m);
    D_ASSERT(getState(stateObj).isFailed() || ( inDomain(d, lower_bound(d)) && inDomain(d, upper_bound(d)) ) );
if((i < lower_bound(d)) || (i > upper_bound(d)) || ! (bms_array->ifMember_remove(var_offset[d.var_num] + i) ))
    {
#ifdef DEBUG
      cout << "Exiting removeFromDomain: " << d.var_num << " nothing to do" << endl;
#endif
      return;
    }
#ifdef FULL_DOMAIN_TRIGGERS
    trigger_list.push_domain_removal(d.var_num, i);
    reduce_dom_size(d);
#endif
#ifndef NO_DOMAIN_TRIGGERS
    trigger_list.push_domain_changed(d.var_num);
#endif
    D_ASSERT( ! bms_array->isMember(var_offset[d.var_num] + i));

    domain_bound_type up_bound = upper_bound(d);
    if(i == up_bound)
    {
      upper_bound(d) = find_new_upper_bound(d);
      trigger_list.push_upper(d.var_num, up_bound - upper_bound(d));
    }

    domain_bound_type low_bound = lower_bound(d);
    if(i == low_bound)
    {
      lower_bound(d) = find_new_lower_bound(d);
      trigger_list.push_lower(d.var_num, lower_bound(d) - low_bound);
    }

    if(upper_bound(d) == lower_bound(d)) {
      trigger_list.push_assign(d.var_num, getAssignedValue(d));
    }

    D_ASSERT(getState(stateObj).isFailed() || ( inDomain(d, lower_bound(d)) && inDomain(d, upper_bound(d)) ) );

#ifdef DEBUG
    cout << "Exiting removeFromDomain: " << d.var_num << " " << i << " ["
         << lower_bound(d) << ":" << upper_bound(d) << "] original ["
         << getInitialMin(d) << ":" << getInitialMax(d) << "]"
         << endl;
    bms_array->print_state();
#endif
    return;
  }

  BOOL validAssignment(BigRangeVarRef_internal d, DomainInt offset, DomainInt lower, DomainInt upper)
  {
    D_ASSERT(getState(stateObj).isFailed() || ( inDomain(d, lower_bound(d)) && inDomain(d, upper_bound(d)) ) );
    if(!inDomain(d,offset))
    {
      getState(stateObj).setFailed(true);
      return false;
    }
    if(offset == upper && offset == lower)
      return false;
    if(offset > upper || offset < lower)
    {
      getState(stateObj).setFailed(true);
      return false;
    }
    return true;
  }

  void propagateAssign(BigRangeVarRef_internal d, DomainInt offset)
  {
    DomainInt lower = lower_bound(d);
    DomainInt upper = upper_bound(d);
    if(!validAssignment(d, offset, lower, upper)) return;
    commonAssign(d, offset, lower, upper);
  }

  void uncheckedAssign(BigRangeVarRef_internal d, DomainInt i)
  {
    D_ASSERT(inDomain(d,i));
    D_ASSERT(!isAssigned(d));
    commonAssign(d,i, lower_bound(d), upper_bound(d));
  }

  void decisionAssign(BigRangeVarRef_internal d, DomainInt i)
  {
    D_ASSERT(inDomain(d,i));
    D_ASSERT(!isAssigned(d));
    commonAssign(d,i, lower_bound(d), upper_bound(d));
  }

private:
  // This function just unifies part of propagateAssign and uncheckedAssign
  void commonAssign(BigRangeVarRef_internal d, DomainInt offset, DomainInt lower, DomainInt upper)
  {
#ifdef FULL_DOMAIN_TRIGGERS
    // TODO : Optimise this function to only check values in domain.
    DomainInt domainOffset = var_offset[d.var_num] /*- initial_bounds[d.var_num].first*/;
    for(DomainInt loop = lower; loop <= upper; ++loop)
    {
      // def of inDomain: bms_array->isMember(var_offset[d.var_num] + i - initial_bounds[d.var_num].first);
      if(bms_array->isMember(loop + domainOffset) && loop != offset) {
        trigger_list.push_domain_removal(d.var_num, loop);
        reduce_dom_size(d);
      }
    }
#endif
    trigger_list.push_domain_changed(d.var_num);
    trigger_list.push_assign(d.var_num, offset);

    DomainInt low_bound = lower_bound(d);
    if(offset != low_bound)
    {
      trigger_list.push_lower(d.var_num, offset - low_bound);
      lower_bound(d) = offset;
    }

    DomainInt up_bound = upper_bound(d);
    if(offset != up_bound)
    {
      trigger_list.push_upper(d.var_num, up_bound - offset);
      upper_bound(d) = offset;
    }
    D_ASSERT(getState(stateObj).isFailed() || ( inDomain(d, lower_bound(d)) && inDomain(d, upper_bound(d)) ) );
  }
public:


  void setMax(BigRangeVarRef_internal d, DomainInt offset)
  {
#ifdef DEBUG
    cout << "Calling setMax: " << d.var_num << " " << offset << " ["
         << lower_bound(d) << ":" << upper_bound(d) << "] original ["
         << getInitialMin(d) << ":" << getInitialMax(d) << "]"
         << endl;
    bms_array->print_state();
#endif

    D_ASSERT(getState(stateObj).isFailed() || ( inDomain(d, lower_bound(d)) && inDomain(d, upper_bound(d)) ) );
    DomainInt up_bound = upper_bound(d);
    DomainInt low_bound = lower_bound(d);

    if(offset < low_bound)
    {
      getState(stateObj).setFailed(true);
      return;
    }

    if(offset < up_bound)
    {
#ifdef FULL_DOMAIN_TRIGGERS
      // TODO : Optimise this function to only check values in domain.
      DomainInt domainOffset = var_offset[d.var_num] /*- initial_bounds[d.var_num].first*/;
      for(DomainInt loop = offset + 1; loop <= up_bound; ++loop)
      {
        // Def of inDomain: bms_array->isMember(var_offset[d.var_num] + i - initial_bounds[d.var_num].first);
        if(bms_array->isMember(domainOffset + loop)) {
          trigger_list.push_domain_removal(d.var_num, loop);
          reduce_dom_size(d);
        }
      }
#endif
      upper_bound(d) = offset;
      DomainInt new_upper = find_new_upper_bound(d);
      upper_bound(d) = new_upper;

#ifndef NO_DOMAIN_TRIGGERS
    trigger_list.push_domain_changed(d.var_num);
#endif
       trigger_list.push_upper(d.var_num, up_bound - upper_bound(d));

      if(lower_bound(d) == upper_bound(d)) {
        trigger_list.push_assign(d.var_num, getAssignedValue(d));
      }
    }
    D_ASSERT(getState(stateObj).isFailed() || ( inDomain(d, lower_bound(d)) && inDomain(d, upper_bound(d)) ) );
#ifdef DEBUG
    cout << "Exiting setMax: " << d.var_num << " " << upper_bound(d) << " ["
         << lower_bound(d) << ":" << upper_bound(d) << "] original ["
         << getInitialMin(d) << ":" << getInitialMax(d) << "]"
         << endl;
    bms_array->print_state();
#endif
  }

  void setMin(BigRangeVarRef_internal d, DomainInt offset)
  {
#ifdef DEBUG
    cout << "Calling setMin: " << d.var_num << " " << offset << " ["
         << lower_bound(d) << ":" << upper_bound(d) << "] original ["
         << getInitialMin(d) << ":" << getInitialMax(d) << "]"
         << endl;
    bms_array->print_state();
#endif
    D_ASSERT(getState(stateObj).isFailed() || ( inDomain(d, lower_bound(d)) && inDomain(d, upper_bound(d)) ) );

    DomainInt up_bound = upper_bound(d);
    DomainInt low_bound = lower_bound(d);

    if(offset > up_bound)
    {
      getState(stateObj).setFailed(true);
      return;
    }

    if(offset > low_bound)
    {
#ifdef FULL_DOMAIN_TRIGGERS
      // TODO : Optimise this function to only check values in domain.
      DomainInt domainOffset = var_offset[d.var_num] /*- initial_bounds[d.var_num].first*/;
      for(DomainInt loop = low_bound; loop < offset; ++loop)
      {
        // def of inDomain: bms_array->isMember(var_offset[d.var_num] + i - initial_bounds[d.var_num].first);
        if(bms_array->isMember(loop + domainOffset)) {
          trigger_list.push_domain_removal(d.var_num, loop);
          reduce_dom_size(d);
        }
      }
#endif
    D_ASSERT(getState(stateObj).isFailed() || ( inDomain(d, lower_bound(d)) && inDomain(d, upper_bound(d)) ) );

    lower_bound(d) = offset;
    DomainInt new_lower = find_new_lower_bound(d);
    lower_bound(d) = new_lower;

#ifndef NO_DOMAIN_TRIGGERS
    trigger_list.push_domain_changed(d.var_num);
#endif
    trigger_list.push_lower(d.var_num, lower_bound(d) - low_bound);
    if(lower_bound(d) == upper_bound(d)) {
      trigger_list.push_assign(d.var_num, getAssignedValue(d));
    }
    }
    D_ASSERT(getState(stateObj).isFailed() || ( inDomain(d, lower_bound(d)) && inDomain(d, upper_bound(d)) ) );
#ifdef DEBUG
    cout << "Exiting setMin: " << d.var_num << " " << lower_bound(d) << " ["
         << lower_bound(d) << ":" << upper_bound(d) << "] original ["
         << getInitialMin(d) << ":" << getInitialMax(d) << "]"
         << endl;
    bms_array->print_state();
#endif
  }

  BigRangeVarRef get_var_num(DomainInt i);

  UnsignedSysInt var_count()
  { return var_count_m; }

  BigRangeVarRef get_new_var(DomainInt i, DomainInt j);

  void addTrigger(BigRangeVarRef_internal b, Trigger t, TrigType type)
  { D_ASSERT(lock_m); trigger_list.add_trigger(b.var_num, t, type);  }

#ifdef DYNAMICTRIGGERS
  void addDynamicTrigger(BigRangeVarRef_internal b, DynamicTrigger* t, TrigType type, DomainInt pos = NoDomainValue BT_FUNDEF)
  {
    D_ASSERT(lock_m);
    D_ASSERT(b.var_num >= 0);
    D_ASSERT(b.var_num <= (SysInt)var_count_m);
    D_ASSERT(type != DomainRemoval || (pos >= getInitialMin(b) && pos <= getInitialMax(b)));
    trigger_list.addDynamicTrigger(b.var_num, t, type, pos BT_CALL);
  }
#endif

  vector<AbstractConstraint*>* getConstraints(const BigRangeVarRef_internal& b)
  { return &constraints[b.var_num]; }

  void addConstraint(const BigRangeVarRef_internal& b, AbstractConstraint* c)
  {
    constraints[b.var_num].push_back(c);
#ifdef WDEG
     wdegs[b.var_num] += c->getWdeg(); //add constraint score to base var wdeg
#endif
  }

  DomainInt getBaseVal(const BigRangeVarRef_internal& b, DomainInt v) const
  {
    D_ASSERT(inDomain(b, v));
    return v;
  }

  Var getBaseVar(const BigRangeVarRef_internal& b) const
  { return Var(VAR_DISCRETE, b.var_num); }

  vector<Mapper> getMapperStack() const
  { return vector<Mapper>(); }


#ifdef WDEG
  DomainInt getBaseWdeg(const BigRangeVarRef_internal& b)
  { return wdegs[b.var_num]; }

  void incWdeg(const BigRangeVarRef_internal& b)
  { wdegs[b.var_num]++; }
#endif

  ~BigRangeVarContainer() {
    for(UnsignedSysInt i=0; i < var_count_m ; i++) {
         // should delete space really!
    } ;
  }
};

template<typename T>
inline BigRangeVarRef
BigRangeVarContainer<T>::get_var_num(DomainInt i)
{
  D_ASSERT(i < (DomainInt)var_count_m);
  return BigRangeVarRef(BigRangeVarRef_internal(this, i));
}
