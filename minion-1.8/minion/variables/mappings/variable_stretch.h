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



#include "../../constraints/constraint_abstract.h"

template<typename T>
struct MultiplyHelp
{
static inline DomainInt round_down(DomainInt val, DomainInt divisor)
{
  if(val > 0)
    return val / divisor;

  DomainInt newval = val / divisor;
  if(newval * divisor == val)
    return newval;

  return newval - 1;
}

static inline DomainInt round_up(DomainInt val, DomainInt divisor)
{
  D_ASSERT(divisor > 0);
  if(val < 0)
    return val / divisor;

  DomainInt newval = val / divisor;
  if(newval*divisor == val)
    return newval;

  return newval + 1;
}

static inline DomainInt divide_exact(DomainInt val, DomainInt divisor)
{
  D_ASSERT(val % divisor == 0);
  return val / divisor;
}
};
/*
template<>
struct MultiplyHelp<BoolVarRef>
{
  static inline SysInt round_down(SysInt val, SysInt divisor)
  {
    if(val < divisor)
      return 0;
    else
      return 1;
  }

  static inline SysInt round_up(SysInt val, SysInt divisor)
  {
    if(
  }

};*/

#if 0
struct TrivialMapData
{
  SysInt multiply()
    { return 1 };

  SysInt shift()
    { return 1 };

  static BoundType ;
    { return true; }

    static bool MultEqualsOne = true;
};

template<typename VarRef, typename DataMap = TrivialDataMap>
{
  static const BOOL isBool = VarRef::isBool;
  static const BoundType isBoundConst = MapData::BoundType || VarRef::isBoundConst;
  BOOL isBound() const
  { return MapData::BoundType || data.isBound(); }

  VarRef data;
  DataMap dataMap;
  MultiplyVar(const VarRef& _data, DataMap _dataMap) : data(_data), dataMap(_dataMap)
  {
    D_ASSERT(DOMAIN_CHECK(checked_cast<BigInt>(data.getInitialMax()) * dataMap.multiply() + dataMap.shift()));
    D_ASSERT(DOMAIN_CHECK(checked_cast<BigInt>(data.getInitialMin()) * dataMap.multiply() + dataMap.shift()));
    D_ASSERT(Multiply != 0);
  }

  MultiplyVar() : data()
  { }

  MultiplyVar(const MultiplyVar& b) : data(b.data), dataMap(b.dataMap)
  { }

  BOOL isAssigned() const
  { return data.isAssigned(); }

  DomainInt getAssignedValue() const
  { return data.getAssignedValue() * dataMap.multiply() + dataMap.shift(); }

  BOOL isAssignedValue(DomainInt i) const
  {
    if(!data.isAssigned()) return false;
      return this->getAssignedValue() == i;
  }

  BOOL inDomain(DomainInt b) const
  {
    if((b - dataMap.shift()) % dataMap.multiply() != 0)
        return false;
      return data.inDomain(MapHelp::divide_exact(b - dataMap.shift(), dataMap));
  }

  BOOL inDomain_noBoundCheck(DomainInt b) const
  {
    if(b % dataMap.multiply() != 0)
      return false;
    return data.inDomain(MapHelp::divide_exact(b - dataMap.shift(), dataMap));
  }

  DomainInt getDomSize() const
  { return data.getDomSize(); }

  DomainInt getMax() const
  {
    if(dataMap.multiply() >= 0)
      return data.getMax() * dataMap.multiply() + dataMap.shift();
    else
      return data.getMin() * dataMap.multiply() + dataMap.shift();
  }

  DomainInt getMin() const
  {
    if(dataMap.multiply() >= 0)
      return data.getMin() * dataMap.multiply() + dataMap.shift();
        else
      return data.getMax() * dataMap.multiply() + dataMap.shift();
  }

  DomainInt getInitialMax() const
  {
    if(dataMap.multiply() >= 0)
      return data.getInitialMax() * dataMap.multiply() + dataMap.shift();
    else
      return data.getInitialMin() * dataMap.multiply() + dataMap.shift();
  }

  DomainInt getInitialMin() const
  {
    if(dataMap.multiply() >= 0)
      return data.getInitialMin() * dataMap.multiply() + dataMap.shift();
    else
      return data.getInitialMax() * dataMap.multiply() + dataMap.shift();
  }

  void setMax(DomainInt i)
  {
    if(dataMap.multiply() >= 0)
      data.setMax(MapHelp::round_down(i, dataMap.multiply()));
    else
      data.setMin(MapHelp::round_up(-i, -dataMap.multiply()));
  }

  void setMin(DomainInt i)
  {
    if(Multiply >= 0)
      data.setMin(MapHelp::round_up(i, dataMap.multiply()));
    else
      data.setMax(MapHelp::round_down(-i, dataMap.multiply()));
  }

  void uncheckedAssign(DomainInt b)
  {
    D_ASSERT(b % dataMap.multiply() == 0);
    data.uncheckedAssign(MultiplyHelp<VarRef>::divide_exact(b, Multiply));
  }

  void propagateAssign(DomainInt b)
  { data.propagateAssign(MultiplyHelp<VarRef>::divide_exact(b, Multiply)); }

  void removeFromDomain(DomainInt)
  { FAIL_EXIT(); }

  void addTrigger(Trigger t, TrigType type)
  {
    switch(type)
    {
      case UpperBound:
        if(Multiply>=0)
          data.addTrigger(t, UpperBound);
        else
          data.addTrigger(t, LowerBound);
        break;
      case LowerBound:
        if(Multiply>=0)
          data.addTrigger(t, LowerBound);
        else
          data.addTrigger(t, UpperBound);
        break;
      case Assigned:
      case DomainChanged:
        data.addTrigger(t, type);
    }
  }

#ifdef DYNAMICTRIGGERS
  void addDynamicTrigger(DynamicTrigger* t, TrigType type, DomainInt pos = NoDomainValue BT_FUNDEF)
  {  data.addDynamicTrigger(t, type, pos); }
#endif


  friend std::ostream& operator<<(std::ostream& o, const MultiplyVar& n)
  { return o << "Mult:" << n.data << "*" << n.Multiply; }

  DomainInt getDomainChange(DomainDelta d)
  { return abs(Multiply) * data.getDomainChange(d); }

  vector<AbstractConstraint*>* getConstraints()
  { return data.getConstraints(); }

  void addConstraint(AbstractConstraint* c)
  { data.addConstraint(c); }

  VarIdent getIdent()
  { return VarIdent(stretchT, Multiply, data.getIdent()); }

#ifdef WDEG
  DomainInt getBaseWdeg()
  { return data.getBaseWdeg(); }

  void incWdeg()
  { data.incWdeg(); }
#endif
};

#endif

template<typename VarRef>
struct MultiplyVar
{
  static const BOOL isBool = true;
  static const BoundType isBoundConst = Bound_Yes;
  BOOL isBound() const
  { return true; }

  AnyVarRef popOneMapper() const
  { return data; }

  VarRef data;
  DomainInt Multiply;
  MultiplyVar(const VarRef& _data, DomainInt _Multiply) : data(_data), Multiply(_Multiply)
  {
    DOMAIN_CHECK(checked_cast<BigInt>(data.getInitialMax()) * checked_cast<BigInt>(Multiply));
    DOMAIN_CHECK(checked_cast<BigInt>(data.getInitialMin()) * checked_cast<BigInt>(Multiply));
    CHECK(Multiply != 0, "Cannot divide variable by 0");
  }

  MultiplyVar() : data()
  { Multiply = 0;}

  MultiplyVar(const MultiplyVar& b) : data(b.data), Multiply(b.Multiply)
  { }

  BOOL isAssigned() const
  { return data.isAssigned(); }

  DomainInt getAssignedValue() const
  { return data.getAssignedValue() * Multiply; }

  BOOL isAssignedValue(DomainInt i) const
  {
    if(!data.isAssigned()) return false;

    return data.getAssignedValue() == i * Multiply;
  }

  BOOL inDomain(DomainInt b) const
  {
    if(b % Multiply != 0)
      return false;
    return data.inDomain(MultiplyHelp<VarRef>::divide_exact(b, Multiply));
  }

  BOOL inDomain_noBoundCheck(DomainInt b) const
  {
    if(b % Multiply != 0)
      return false;
    return data.inDomain(MultiplyHelp<VarRef>::divide_exact(b, Multiply));
  }

  DomainInt getDomSize() const
  { return data.getDomSize(); }

  DomainInt getMax() const
  {
    if(Multiply >= 0)
      return data.getMax() * Multiply;
    else
      return data.getMin() * Multiply;
  }

  DomainInt getMin() const
  {
    if(Multiply >= 0)
      return data.getMin() * Multiply;
    else
      return data.getMax() * Multiply;
  }

  DomainInt getInitialMax() const
  {
    if(Multiply >= 0)
      return data.getInitialMax() * Multiply;
    else
      return data.getInitialMin() * Multiply;
  }

  DomainInt getInitialMin() const
  {
    if(Multiply >= 0)
      return data.getInitialMin() * Multiply;
    else
      return data.getInitialMax() * Multiply;
  }

  void setMax(DomainInt i)
  {
    if(Multiply >= 0)
      data.setMax(MultiplyHelp<VarRef>::round_down(i, Multiply));
    else
      data.setMin(MultiplyHelp<VarRef>::round_up(-i, -Multiply));
  }

  void setMin(DomainInt i)
  {
    if(Multiply >= 0)
      data.setMin(MultiplyHelp<VarRef>::round_up(i, Multiply));
    else
      data.setMax(MultiplyHelp<VarRef>::round_down(-i, -Multiply));
  }

  void uncheckedAssign(DomainInt b)
  {
    D_ASSERT(b % Multiply == 0);
    data.uncheckedAssign(MultiplyHelp<VarRef>::divide_exact(b, Multiply));
  }

  void propagateAssign(DomainInt b)
  { data.propagateAssign(MultiplyHelp<VarRef>::divide_exact(b, Multiply)); }

  void decisionAssign(DomainInt b)
  { data.decisionAssign(MultiplyHelp<VarRef>::divide_exact(b, Multiply)); }

  void removeFromDomain(DomainInt)
  { FAIL_EXIT(); }

  void addTrigger(Trigger t, TrigType type)
  {
    switch(type)
    {
      case UpperBound:
        if(Multiply>=0)
          data.addTrigger(t, UpperBound);
        else
          data.addTrigger(t, LowerBound);
        break;
      case LowerBound:
        if(Multiply>=0)
          data.addTrigger(t, LowerBound);
        else
          data.addTrigger(t, UpperBound);
        break;
      case Assigned:
      case DomainChanged:
        data.addTrigger(t, type);
      break;
      default:
      D_FATAL_ERROR("Fatal error in 'stretch' wrapper");
    }
  }

#ifdef DYNAMICTRIGGERS
  void addDynamicTrigger(DynamicTrigger* t, TrigType type, DomainInt pos = NoDomainValue BT_FUNDEF)
  {
    switch(type)
    {
      case UpperBound:
      if(Multiply>=0)
        data.addDynamicTrigger(t, UpperBound, pos BT_CALL);
      else
        data.addDynamicTrigger(t, LowerBound, pos BT_CALL);
      break;
      case LowerBound:
      if(Multiply>=0)
        data.addDynamicTrigger(t, LowerBound, pos BT_CALL);
      else
        data.addDynamicTrigger(t, UpperBound, pos BT_CALL);
        break;
      case Assigned:
      case DomainChanged:
        data.addDynamicTrigger(t, type, pos BT_CALL);
        break;
      case DomainRemoval:
        data.addDynamicTrigger(t, DomainRemoval,
                               MultiplyHelp<VarRef>::divide_exact(pos, Multiply) BT_CALL);
        break;
      default:
        D_FATAL_ERROR("Broken dynamic trigger");
    }
  }
#endif



  friend std::ostream& operator<<(std::ostream& o, const MultiplyVar& n)
  { return o << "Mult:" << n.data << "*" << n.Multiply; }

  DomainInt getDomainChange(DomainDelta d)
  { return abs(checked_cast<SysInt>(Multiply)) * data.getDomainChange(d); }

  vector<AbstractConstraint*>* getConstraints()
  { return data.getConstraints(); }

  void addConstraint(AbstractConstraint* c)
  { data.addConstraint(c); }

  DomainInt getBaseVal(DomainInt v) const
  { return data.getBaseVal(MultiplyHelp<VarRef>::divide_exact(v, Multiply)); }

  Var getBaseVar() const { return data.getBaseVar(); }

  vector<Mapper> getMapperStack() const
  {
    vector<Mapper> v = data.getMapperStack();
    v.push_back(Mapper(MAP_MULT, (DomainInt)Multiply));
    return v;
  }


#ifdef WDEG
  DomainInt getBaseWdeg()
  { return data.getBaseWdeg(); }

  void incWdeg()
  { data.incWdeg(); }
#endif
};

template<typename T>
struct MultiplyType
{ typedef MultiplyVar<T> type; };

template<typename T>
struct MultiplyType<vector<T> >
{ typedef vector<MultiplyVar<T> > type; };

#ifdef LIGHT_VECTOR
template<typename T>
struct MultiplyType<vector<T> >
{ typedef vector<MultiplyVar<T> > type; };
#endif

template<typename T, std::size_t i>
struct MultiplyType<std::array<T, i> >
{ typedef std::array<MultiplyVar<T>, i> type; };


template<typename VRef>
typename MultiplyType<VRef>::type
MultiplyVarRef(VRef var_ref, SysInt i)
{ return MultiplyVar<VRef>(var_ref, i); }

template<typename VarRef>
vector<MultiplyVar<VarRef> >
MultiplyVarRef(const vector<VarRef>& var_array, const vector<DomainInt>& multiplies)
{
  vector<MultiplyVar<VarRef> > Multiply_array(var_array.size());
  for(UnsignedSysInt i = 0; i < var_array.size(); ++i)
    Multiply_array[i] = MultiplyVarRef(var_array[i], multiplies[i]);
  return Multiply_array;
}

#ifdef LIGHT_VECTOR
template<typename VarRef>
vector<MultiplyVar<VarRef> >
MultiplyVarRef(const vector<VarRef>& var_array, const vector<DomainInt>& multiplies)
{
  vector<MultiplyVar<VarRef> > Multiply_array(var_array.size());
  for(UnsignedSysInt i = 0; i < var_array.size(); ++i)
    Multiply_array[i] = MultiplyVarRef(var_array[i], multiplies[i]);
  return Multiply_array;
}
#endif

template<typename VarRef, std::size_t i>
std::array<MultiplyVar<VarRef>, i>
MultiplyVarRef(const std::array<VarRef, i>& var_array, const std::array<SysInt, i>& multiplies)
{
  std::array<MultiplyVar<VarRef>, i> Multiply_array;
  for(UnsignedSysInt l = 0; l < i; ++l)
    Multiply_array[l] = MultiplyVarRef(var_array[l], multiplies[i]);
  return Multiply_array;
}

