package org.intermine.metadata;

/*
 * Copyright (C) 2002-2004 FlyMine
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  See the LICENSE file for more
 * information or http://www.gnu.org/copyleft/lesser.html.
 *
 */

import junit.framework.TestCase;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Iterator;

public class ModelTest extends TestCase
{
    private static final Set EMPTY_SET = Collections.EMPTY_SET;
    private String uri = "http://www.intermine.org/model/testmodel#";

    public ModelTest(String arg) {
        super(arg);
    }

    public void testGetInstanceByWrongName() throws Exception {
        try {
            Model model = Model.getInstanceByName("wrong_name");
            fail("Expected IllegalArgumentException, wrong model name");
        } catch (IllegalArgumentException e) {
        }
    }

    public void testGetInstanceByNameSameInstance() throws Exception {
        Model model1 = Model.getInstanceByName("testmodel");
        Model model2 = Model.getInstanceByName("testmodel");
        assertTrue(model1 == model2);
    }

    public void testContructNullArguments() throws Exception {
        ClassDescriptor cld1 = new ClassDescriptor("Class1", null, false, new HashSet(), new HashSet(), new HashSet());
        ClassDescriptor cld2 = new ClassDescriptor("Class2", null, false, new HashSet(), new HashSet(), new HashSet());
        Set clds = new HashSet(Arrays.asList(new Object[] {cld1, cld2}));

        try {
            Model model = new Model(null, uri, clds);
            fail("Expected NullPointerException, name was null");
        } catch(NullPointerException e) {
        }

        try {
            Model model = new Model("", uri, clds);
            fail("Expected IllegalArgumentException, name was empty string");
        } catch(IllegalArgumentException e) {
        }

        try {
            Model model = new Model("model", uri, null);
            fail("Expected NullPointerException, name was null");
        } catch(NullPointerException e) {
        }
        try {
            Model model = new Model("testmodel", "", clds);
            fail("Expected IllegalArgumentException, nameSpace was empty string");
        } catch(IllegalArgumentException e) {
        }

        try {
            Model model = new Model("testmodel", null, clds);
            fail("Expected NullPointerException, nameSpace was null");
        } catch(NullPointerException e) {
        }
    }

    public void testGetClassDescriptorByName() throws Exception {
        ClassDescriptor cld1 = new ClassDescriptor("Class1", null, false, new HashSet(), new HashSet(), new HashSet());
        ClassDescriptor cld2 = new ClassDescriptor("Class2", null, false, new HashSet(), new HashSet(), new HashSet());
        Set clds = new HashSet(Arrays.asList(new Object[] {cld1, cld2}));
        Model model = new Model("model", uri, clds);

        assertEquals(cld1, model.getClassDescriptorByName("Class1"));
        assertEquals(cld2, model.getClassDescriptorByName("Class2"));
    }

    public void testGetClassDescriptorByWrongName() throws Exception {
        ClassDescriptor cld1 = new ClassDescriptor("Class1", null, false, new HashSet(), new HashSet(), new HashSet());
        ClassDescriptor cld2 = new ClassDescriptor("Class2", null, false, new HashSet(), new HashSet(), new HashSet());
        Set clds = new HashSet(Arrays.asList(new Object[] {cld1, cld2}));
        Model model = new Model("model", uri, clds);

        assertTrue(null == model.getClassDescriptorByName("WrongName"));
    }

    public void testGetClassDescriptorsForClass() throws Exception {
        Model model = Model.getInstanceByName("testmodel");
        Set cds = model.getClassDescriptorsForClass(org.intermine.model.testmodel.CEO.class);
        Set expectedCdNames = new HashSet();
        expectedCdNames.add("org.intermine.model.testmodel.ImportantPerson");
        expectedCdNames.add("org.intermine.model.testmodel.Employable");
        expectedCdNames.add("org.intermine.model.testmodel.HasAddress");
        expectedCdNames.add("org.intermine.model.testmodel.CEO");
        expectedCdNames.add("org.intermine.model.testmodel.Employee");
        expectedCdNames.add("org.intermine.model.testmodel.Thing");
        expectedCdNames.add("org.intermine.model.InterMineObject");
        expectedCdNames.add("org.intermine.model.testmodel.HasSecretarys");
        expectedCdNames.add("org.intermine.model.testmodel.Manager");
        Set cdNames = new HashSet();
        for (Iterator iter = cds.iterator(); iter.hasNext(); ) {
            cdNames.add(((ClassDescriptor) iter.next()).getName());
        }
        assertEquals(expectedCdNames, cdNames);
    }

    public void testEquals() throws Exception {
        Model m1 = new Model("flibble", uri, EMPTY_SET);
        Model m2 = new Model("flibble", uri, EMPTY_SET);
        Model m3 = new Model("flobble", uri, EMPTY_SET);
        Model m4 = new Model("flibble", uri, Collections.singleton(new ClassDescriptor("class1", null, true, EMPTY_SET, EMPTY_SET, EMPTY_SET)));

        assertEquals(m1, m2);
        assertEquals(m1.hashCode(), m2.hashCode());
        assertFalse(m1.equals(m3));
        assertTrue(!m1.equals(m3));
        assertTrue(!m1.equals(m4));
    }

    public void testToString() throws Exception {
        ClassDescriptor cld1 = new ClassDescriptor("Class1", null, false, new HashSet(), new HashSet(), new HashSet());
        ClassDescriptor cld2 = new ClassDescriptor("Class2", null, false, new HashSet(), new HashSet(), new HashSet());
        Set clds = new LinkedHashSet(Arrays.asList(new Object[] {cld1, cld2}));
        Model model = new Model("model", uri, clds);

        String expected = "<model name=\"model\" namespace=\"http://www.intermine.org/model/testmodel#\">"
            + "<class name=\"Class1\" is-interface=\"false\"></class>"
            + "<class name=\"Class2\" is-interface=\"false\"></class>"
            + "</model>";

        assertEquals(expected, model.toString());
    }
}
