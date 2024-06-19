/*
 * Copyright (c) 2024, WSO2 LLC. (http://www.wso2.com)
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package io.ballerina.lib.ldap;

import com.unboundid.ldap.sdk.AsyncRequestID;
import com.unboundid.ldap.sdk.AsyncSearchResultListener;
import com.unboundid.ldap.sdk.Attribute;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.ResultCode;
import com.unboundid.ldap.sdk.SearchResult;
import com.unboundid.ldap.sdk.SearchResultEntry;
import com.unboundid.ldap.sdk.SearchResultReference;
import io.ballerina.runtime.api.Future;
import io.ballerina.runtime.api.creators.ValueCreator;
import io.ballerina.runtime.api.types.ArrayType;
import io.ballerina.runtime.api.utils.ValueUtils;
import io.ballerina.runtime.api.values.BArray;
import io.ballerina.runtime.api.values.BError;
import io.ballerina.runtime.api.values.BMap;
import io.ballerina.runtime.api.values.BString;
import io.ballerina.runtime.api.values.BTypedesc;

import static io.ballerina.lib.ldap.Client.processAttribute;
import static io.ballerina.lib.ldap.Utils.ENTRY_NOT_FOUND;
import static io.ballerina.lib.ldap.Utils.createError;

/**
 * Callback class to handle search entry values asynchronously.
 */
public class CustomSearchEntryListener implements AsyncSearchResultListener {
    private final Future future;
    private final BArray array;
    private BError error;
    private final BTypedesc typeDesc;
    private final String dN;

    public CustomSearchEntryListener(Future future, BTypedesc typeDesc, String dN) {
        this.future = future;
        this.array = ValueCreator.createArrayValue((ArrayType) typeDesc.getDescribingType());
        this.dN = dN;
        this.typeDesc = typeDesc;
        this.error = null;
    }

    @Override
    public void searchResultReceived(AsyncRequestID requestID, SearchResult searchResult) {
        if (error != null) {
            future.complete(error);
            return;
        }
        if (!searchResult.getResultCode().equals(ResultCode.SUCCESS)) {
            LDAPException ldapException = new LDAPException(searchResult);
            future.complete(Utils.createError(ldapException.getMessage(), ldapException));
            return;
        }
        if (array.isEmpty()) {
            String errorMessage = ENTRY_NOT_FOUND + dN;
            LDAPException ldapException = new LDAPException(ResultCode.OTHER, errorMessage);
            future.complete(createError(ldapException.getMessage(), ldapException));
            return;
        }
        future.complete(array);
    }

    @Override
    public void searchEntryReturned(SearchResultEntry searchEntry) {
        try {
            BMap<BString, Object> entry = ValueCreator.createMapValue();
            for (Attribute attribute : searchEntry.getAttributes()) {
                processAttribute(attribute, entry);
            }
            ArrayType arrayType = (ArrayType) typeDesc.getDescribingType();
            array.append(ValueUtils.convert(entry, arrayType.getElementType()));
        } catch (Exception e) {
            this.error = createError(e.getMessage(), e);
        }
    }

    @Override
    public void searchReferenceReturned(SearchResultReference searchReference) {
    }
}
