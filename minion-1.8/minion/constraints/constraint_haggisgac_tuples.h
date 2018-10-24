/*
* Minion http://minion.sourceforge.net
* Copyright (C) 2006-13
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

#ifndef _HAGGIS_GAC_TUPLES_CHUDSCDFDSAFDSA
#define _HAGGIS_GAC_TUPLES_CHUDSCDFDSAFDSA

 struct SupportDeref
    {
        template<typename T>
        bool operator()(const T& lhs, const T& rhs)
        { return *lhs < *rhs; }
    };

struct HaggisGACTuples
{
    typedef vector<vector<vector<vector<pair<SysInt, DomainInt> > * > > > tuple_list_type;


    tuple_list_type tuple_list_cpy;

    const tuple_list_type& get_tl() const
    { return tuple_list_cpy; }

    template<typename Vars, typename Data>
    HaggisGACTuples(const Vars& vars, Data data)
    {
        // Read in the short supports.
        vector<vector<pair<SysInt, DomainInt> > * > shortsupports;
        
        const vector<vector<pair<SysInt, DomainInt> > >& tupleRef = (*data->tuplePtr());
        
        for(SysInt i=0; i<(SysInt)tupleRef.size(); i++) {
            shortsupports.push_back(new vector<pair<SysInt, DomainInt> >(tupleRef[i]));
        }

        
        // Sort it. Might not work when it's pointers.
        for(SysInt i=0; i<(SysInt)shortsupports.size(); i++) {
            sort(shortsupports[i]->begin(), shortsupports[i]->end());
        }
        sort(shortsupports.begin(), shortsupports.end(), SupportDeref());
        
        tuple_list_cpy.resize(vars.size());
        for(SysInt var=0; var<(SysInt)vars.size(); var++) {
            SysInt domsize = checked_cast<SysInt>(vars[var].getInitialMax()-vars[var].getInitialMin()+1);
            tuple_list_cpy[var].resize(domsize);
            
            for(DomainInt val=vars[var].getInitialMin(); val<=vars[var].getInitialMax(); val++) {
                // get short supports relevant to var,val.
                for(SysInt i=0; i<(SysInt)shortsupports.size(); i++) {
                    bool varin=false;
                    bool valmatches=true;
                    
                    vector<pair<SysInt, DomainInt> > & shortsup=*(shortsupports[i]);
                    
                    for(SysInt j=0; j<(SysInt)shortsup.size(); j++) {
                        if(shortsup[j].first==var) {
                            varin=true;
                            if(shortsup[j].second!=val) {
                                valmatches=false;
                            }
                        }
                    }
                    
                    if(!varin || valmatches) {
                        // If the support doesn't include the var, or it 
                        // does include var,val then add it to the list.
                        tuple_list_cpy[var][checked_cast<SysInt>(val-vars[var].getInitialMin())].push_back(shortsupports[i]);
                    }
                }
            }
        }
    }
};

template<typename Vars>
inline HaggisGACTuples* ShortTupleList::getHaggisData(const Vars& vars)
{
    vector<pair<DomainInt, DomainInt> > doms;

    for(SysInt i = 0; i < (SysInt)vars.size(); ++i)
        doms.push_back(std::make_pair(vars[i].getInitialMin(), vars[i].getInitialMax()));

    if(hgt.count(doms) == 0)
    {
        hgt[doms] = new HaggisGACTuples(vars, this);
    }

    return hgt[doms];
}

#endif
