package net.starmen.pkhack.eb;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JTextField;

import net.starmen.pkhack.AbstractRom;
import net.starmen.pkhack.HackModule;
import net.starmen.pkhack.XMLPreferences;

public class EventMusicEditor extends EbHackModule implements ActionListener
{
    public EventMusicEditor(AbstractRom rom, XMLPreferences prefs)
    {
        super(rom, prefs);
    }
    
    private EventMusicEntry[] entries = new EventMusicEntry[164];
    private JComboBox entryChooser, corrChooser, musicChooser;
    private JTextField flagField;
    private JCheckBox reverseCheck;
    private JButton add, del, inc, dec;
    private boolean updatingCoors = false, updatingEntries = false;
    public static final int asmPointer = 0x6b39;

	protected void init()
	{
		initMusicNames(rom.getPath());
		readFromRom();
		
		mainWindow = createBaseWindow(this);
		mainWindow.setTitle(this.getDescription());
		
		JPanel panel = new JPanel();
		panel.setLayout(
				new BoxLayout(panel, BoxLayout.Y_AXIS));
		
		String[] entryNames = new String[entries.length];
		for (int i = 0; i < entryNames.length; i++)
			entryNames[i] = "#" + i + " (" + entries[i].getDefaultName() + ")";
		entryChooser = new JComboBox(entryNames);
		entryChooser.addActionListener(this);
		panel.add(HackModule.getLabeledComponent(
				"Entry: ", entryChooser));
		
		corrChooser = new JComboBox();
		corrChooser.addActionListener(this);
		panel.add(HackModule.getLabeledComponent(
				"Coorelation: ", corrChooser));
		
		JPanel tmp = new JPanel();
		add = new JButton("New");
		add.addActionListener(this);
		tmp.add(add);
		del = new JButton("Delete");
		del.addActionListener(this);
		tmp.add(del);
		inc = new JButton("Raise");
		inc.addActionListener(this);
		tmp.add(inc);
		dec = new JButton("Lower");
		dec.addActionListener(this);
		tmp.add(dec);
		panel.add(tmp);
		
		
		flagField = HackModule.createSizedJTextField(3, true);
		panel.add(HackModule.getLabeledComponent(
				"Event Flag: ", flagField));
		
		reverseCheck = new JCheckBox();
		panel.add(HackModule.getLabeledComponent(
				"Reverse Flag?: ", reverseCheck));
		
		musicChooser = new JComboBox(musicNames);
		panel.add(HackModule.getLabeledComponent(
				"Music: ", musicChooser));
		
		mainWindow.getContentPane().add(
				panel, BorderLayout.CENTER);
		mainWindow.pack();
	}
	
    public static void initMusicNames(String romPath)
    {
        readArray(DEFAULT_BASE_DIR, "musiclisting.txt", romPath, true, musicNames);
    }

	public String getVersion()
	{
		return "0.1";
	}

	public String getDescription()
	{
		return "Music/Flags Correlations Editor";
	}

	public String getCredits()
	{
		return "Written by Mr. Tenda";
	}
	
	public void show()
	{
		super.show();
		
		readFromRom();
		updateCorrelationChooser();
		updateComponents();
		
		mainWindow.setVisible(true);
	}
	
	public void show(Object entry)
	{
		show();
		entryChooser.setSelectedIndex(((Integer) entry).intValue());
	}

	public void hide()
	{
		mainWindow.setVisible(false);
	}
	
	public void readFromRom()
	{
		int ptrTable = HackModule.toRegPointer(rom.readMulti(asmPointer, 3)) + 2;
		for (int i = 0; i < entries.length; i++)
		{
			ArrayList correlations = new ArrayList();
			int address = rom.readMulti(ptrTable + (i * 2), 2) + 0xf0200;
			boolean reading = true;
			int j = 0;
			byte defaultMusic = 0;
			while (reading)
			{
				int flag = rom.readMulti(address + (j * 4), 2);
				boolean reversed = false;
				if (flag >= 0x8000)
				{
					reversed = true;
					flag -= 0x8000;
				}
				byte music = rom.readByte(address + (j * 4) + 2);
				if (flag == 0)
				{
					reading = false;
					defaultMusic = music;
				}
				else
					correlations.add(
							new EventMusicEntry.Correlation(
									(short) flag, reversed, music));
				j++;
			}
			entries[i] = new EventMusicEntry(correlations, defaultMusic, (j * 4));
		}
	}
	
	public void saveChanges()
	{
		EventMusicEntry entry =
			entries[entryChooser.getSelectedIndex()];
		if (corrChooser.getSelectedIndex() < entry.size())
		{
			EventMusicEntry.Correlation corr = 
				entry.getCorrelation(
						corrChooser.getSelectedIndex());
			corr.setFlag((short) 
					Integer.parseInt(flagField.getText(), 16));
			corr.setReversed(reverseCheck.isSelected());
			corr.setMusic((byte) musicChooser.getSelectedIndex());
		}
		else
		{
			entry.setDefaultMusic((byte) musicChooser.getSelectedIndex());
			updatingEntries = true;
			entryChooser.removeAllItems();
			for (int i = 0; i < entries.length; i++)
				entryChooser.addItem("#" + i + " (" + entries[i].getDefaultName() + ")");
			updatingEntries = false;
		}
		updateCorrelationChooser();
	}
	
	public void writeToRom()
	{
		byte[] pointersData = rom.readByte(
				toRegPointer(rom.readMulti(asmPointer, 3)),
				(entries.length + 1) * 2);
		boolean a = writetoFree(pointersData, asmPointer, 3,
				pointersData.length, pointersData.length, true);
		int pointersLoc = toRegPointer(rom.readMulti(asmPointer, 3)),
			address = 0x58ef;
		for (int i = 0; i < entries.length; i++)
		{
			int pointerLoc = pointersLoc + ((i + 1) * 2);
			int oldPointer = 0xf0200 + rom.readMulti(pointerLoc, 2);
			nullifyArea(oldPointer, entries[i].getOldLength());
			rom.write(pointerLoc, address, 2);
			byte[] data = entries[i].toByteArray();
			rom.write(0xf0200 + address, data);
			address += data.length;
		}
	}
	
	public void updateCorrelationChooser()
	{
		int selected = corrChooser.getSelectedIndex();
		updatingCoors = true;
		EventMusicEntry entry = entries[entryChooser.getSelectedIndex()];
		corrChooser.removeAllItems();
		for (int i = 0; i < entry.size(); i++)
			corrChooser.addItem("#" + i + " (" + entry.getCorrelation(i).getMusicName() + ")");
		corrChooser.addItem("Default (" + entry.getDefaultName() + ")");
		if ((selected >= 0) && (selected <= entry.size()))
			corrChooser.setSelectedIndex(selected);
		else
			corrChooser.setSelectedIndex(0);
		mainWindow.pack();
		updatingCoors = false;
		updateComponents();
	}
	
	public void updateComponents()
	{
		EventMusicEntry entry =
			entries[entryChooser.getSelectedIndex()];
		if (corrChooser.getSelectedIndex() < entry.size())
		{
			EventMusicEntry.Correlation corr = 
				entry.getCorrelation(
						corrChooser.getSelectedIndex());
			flagField.setEnabled(true);
			flagField.setText(Integer.toHexString(corr.getFlag()));
			reverseCheck.setEnabled(true);
			reverseCheck.setSelected(corr.isReversed());
			add.setEnabled(true);
			del.setEnabled(true);
			inc.setEnabled(corrChooser.getSelectedIndex() > 0);
			dec.setEnabled(corrChooser.getSelectedIndex() < entry.size() - 1);
			musicChooser.setSelectedIndex(corr.getMusic() & 0xff);
		}
		else
		{
			flagField.setText("");
			flagField.setEnabled(false);
			reverseCheck.setSelected(false);
			reverseCheck.setEnabled(false);
			add.setEnabled(false);
			del.setEnabled(false);
			inc.setEnabled(false);
			dec.setEnabled(false);
			musicChooser.setSelectedIndex(entry.getDefaultMusic() & 0xff);
		}
	}

	public void actionPerformed(ActionEvent ae)
	{
		if (ae.getActionCommand().equals("apply"))
		{
			System.out.println("Applying changes");
			saveChanges();
			writeToRom();
		}
		else if (ae.getActionCommand().equals("close"))
			hide();
		else if (ae.getSource().equals(entryChooser) && !updatingEntries)
			updateCorrelationChooser();
		else if (ae.getSource().equals(corrChooser) && !updatingCoors)
			updateComponents();
		else if (ae.getSource().equals(add))
		{
			entries[entryChooser.getSelectedIndex()].addCorrelation(
					new EventMusicEntry.Correlation((short) 1, false, (byte) 0));
			updateCorrelationChooser();
		}
		else if (ae.getSource().equals(del))
		{
			entries[entryChooser.getSelectedIndex()].delCorrelation(
					corrChooser.getSelectedIndex());
			updateCorrelationChooser();
		}
		else if (ae.getSource().equals(inc))
		{
			entries[entryChooser.getSelectedIndex()].switchCorrelations(
					corrChooser.getSelectedIndex(),
					corrChooser.getSelectedIndex() - 1);
			corrChooser.setSelectedIndex(corrChooser.getSelectedIndex() - 1);
			updateCorrelationChooser();
		}
		else if (ae.getSource().equals(dec))
		{
			entries[entryChooser.getSelectedIndex()].switchCorrelations(
					corrChooser.getSelectedIndex(),
					corrChooser.getSelectedIndex() + 1);
			corrChooser.setSelectedIndex(corrChooser.getSelectedIndex() + 1);
			updateCorrelationChooser();
		}
	}
	
	public static class EventMusicEntry
	{
		private ArrayList correlations;
		private byte defaultMusic;
		private int oldLength;
		
		public EventMusicEntry(ArrayList correlations, byte defaultMusic, int oldLength)
		{
			this.correlations = correlations;
			this.defaultMusic = defaultMusic;
			this.oldLength = oldLength;
		}
		
		public int getOldLength()
		{
			return oldLength;
		}
		
		public byte getDefaultMusic()
		{
			return defaultMusic;
		}
		
		public String getDefaultName()
		{
			return EventMusicEditor.musicNames[defaultMusic & 0xff];
		}
		
		public void setDefaultMusic(byte defaultMusic)
		{
			this.defaultMusic = defaultMusic;
		}
		
		public Correlation getCorrelation(int num)
		{
			return (Correlation) correlations.get(num);
		}
		
		public void addCorrelation(Correlation corr)
		{
			correlations.add(corr);
		}
		
		public void delCorrelation(int num)
		{
			correlations.remove(num);
		}
		
		public void switchCorrelations(int a, int b)
		{
			Correlation c = getCorrelation(a);
			correlations.set(a, getCorrelation(b));
			correlations.set(b, c);
		}
		
		public int size()
		{
			return correlations.size();
		}
		
		public byte[] toByteArray()
		{
			byte[] out = new byte[(correlations.size() + 1) * 4];
			for (int i = 0; i < correlations.size(); i++)
			{
				Correlation corr = (Correlation) correlations.get(i);
				out[i * 4] = corr.getFlagByte(0);
				out[i * 4 + 1] = corr.getFlagByte(1);
				out[i * 4 + 2] = corr.getMusic();
				out[i * 4 + 3] = 0;
			}
			out[correlations.size() * 4] = 0;
			out[correlations.size() * 4 + 1] = 0;
			out[correlations.size() * 4 + 2] = defaultMusic;
			out[correlations.size() * 4 + 3] = 0;
			return out;
		}
		
		public static class Correlation
		{
			private short flag;
			private boolean reversed;
			private byte music;
			
			public Correlation(short flag, boolean reversed, byte music)
			{
				this.flag = flag;
				this.reversed = reversed;
				this.music = music;
			}
			
			public short getFlag()
			{
				return flag;
			}
			
			public byte getFlagByte(int num)
			{
				if (num == 0)
					return (byte) (flag & 0xff);
				else
				{
					int flagByte = (flag & 0xff00) / 0x100;
					if (reversed)
						return (byte) ((flagByte + 0x80) & 0xff);
					else
						return (byte) (flagByte & 0xff);
				}
			}
			
			public boolean isReversed()
			{
				return reversed;
			}
			
			public byte getMusic()
			{
				return music;
			}
			
			public String getMusicName()
			{
				return EventMusicEditor.musicNames[music & 0xff];
			}
			
			public void setFlag(short flag)
			{
				this.flag = flag;
			}
			
			public void setReversed(boolean reversed)
			{
				this.reversed = reversed;
			}
			
			public void setMusic(byte music)
			{
				this.music = music;
			}
		}
	}
}